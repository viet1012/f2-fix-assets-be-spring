package com.spc.fixedasset.repository;

import com.spc.fixedasset.dto.ImportHistoryResponse;
import com.spc.fixedasset.dto.LastImportResponse;
import com.spc.fixedasset.model.FixedAsset;
import com.spc.fixedasset.model.ImportedFixedAsset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class FixedAssetRepository {
    private static final int BATCH_SIZE=500;
    private static final List<String> IMPORT_COLUMNS=List.of("FAName","PhysicalCount","FAType","KindExpense","KindFixedAsset","DateStart","HistotyCost","NetBookValue","YearDepreciation","Group","EmailChecked","EmailApproved","Div","Factory","Floor","PositionA","PositionAA","PositionAAA","InvoiceNo","Maker","PurchasedFrom","Type","SerialNo","CheckMETI","Photo1","Photo2","Photo3","PhotoJudge","MachineStatus");
    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert historyInsert;
    public FixedAssetRepository(JdbcTemplate jdbc,DataSource ds){this.jdbc=jdbc;this.historyInsert=new SimpleJdbcInsert(ds).withSchemaName("dbo").withTableName("F2_FIXED_ASSET_IMPORT_HISTORY").usingGeneratedKeyColumns("Id");}

    public List<FixedAsset> findAll(){return jdbc.query("""
        SELECT MachineCode AS code, FAName AS name, [Group] AS assetGroup, Floor AS floor,
          CONCAT_WS(' ',NULLIF(LTRIM(RTRIM(PositionA)),''),NULLIF(LTRIM(RTRIM(PositionAA)),''),NULLIF(LTRIM(RTRIM(PositionAAA)),'')) AS position,
          HistotyCost AS cost, Maker AS maker, EmailChecked AS pic, EmailApproved AS picApproved,
          MachineStatus AS status, KindFixedAsset AS kind, Div AS div, Factory AS factory,
          YearDepreciation AS depYears, CONVERT(nvarchar(50),DateStart,120) AS dateStart, PhotoJudge AS photoEval,
          CAST(CASE WHEN LOWER(LTRIM(RTRIM(COALESCE(Photo1,'')))) NOT IN ('','no need','no use')
                      OR LOWER(LTRIM(RTRIM(COALESCE(Photo2,'')))) NOT IN ('','no need','no use')
                      OR LOWER(LTRIM(RTRIM(COALESCE(Photo3,'')))) NOT IN ('','no need','no use') THEN 1 ELSE 0 END AS bit) AS hasPhoto
        FROM dbo.F2_FIXED_ASSET ORDER BY MachineCode
        """,(rs,n)->new FixedAsset(rs.getString("code"),rs.getString("name"),rs.getString("assetGroup"),rs.getString("floor"),rs.getString("position"),rs.getBigDecimal("cost"),rs.getString("maker"),rs.getString("pic"),rs.getString("picApproved"),rs.getString("status"),rs.getString("kind"),rs.getString("div"),rs.getString("factory"),rs.getBigDecimal("depYears"),rs.getString("dateStart"),rs.getString("photoEval"),rs.getBoolean("hasPhoto")));}

    public LastImportResponse findLastSuccessfulImport(){List<LastImportResponse> x=jdbc.query("""
        SELECT TOP (1) Id,SourceName,SheetName,[RowCount],ImportedAt FROM dbo.F2_FIXED_ASSET_IMPORT_HISTORY
        WHERE [Status]='success' ORDER BY ImportedAt DESC,Id DESC
        """,(rs,n)->new LastImportResponse(rs.getLong("Id"),rs.getString("SourceName"),rs.getString("SheetName"),rs.getInt("RowCount"),rs.getTimestamp("ImportedAt").toLocalDateTime()));return x.isEmpty()?null:x.get(0);}

    public List<ImportHistoryResponse> findImportHistory(int limit){int safe=Math.max(1,Math.min(limit,200));return jdbc.query(("""
        SELECT TOP (%d) Id,SourceType,SourceName,SheetName,[RowCount],[Status],ErrorMessage,ImportedAt
        FROM dbo.F2_FIXED_ASSET_IMPORT_HISTORY ORDER BY ImportedAt DESC,Id DESC
        """).formatted(safe),(rs,n)->new ImportHistoryResponse(rs.getLong("Id"),rs.getString("SourceType"),rs.getString("SourceName"),rs.getString("SheetName"),rs.getInt("RowCount"),rs.getString("Status"),rs.getString("ErrorMessage"),rs.getTimestamp("ImportedAt").toLocalDateTime()));}

    public long insertImportHistory(String sourceType,String sourceName,String sheetName,int count,String status,String error,String importedBy){
        MapSqlParameterSource p=new MapSqlParameterSource().addValue("SourceType",sourceType).addValue("SourceName",sourceName).addValue("SheetName",sheetName).addValue("RowCount",count).addValue("Status",status).addValue("ErrorMessage",error).addValue("ImportedAt",Timestamp.valueOf(java.time.LocalDateTime.now())).addValue("ImportedBy",importedBy);
        return historyInsert.executeAndReturnKey(p).longValue();
    }

    public void upsertAssets(List<ImportedFixedAsset> rows,String importedBy){
        Set<String> existing=new HashSet<>(jdbc.queryForList("SELECT MachineCode FROM dbo.F2_FIXED_ASSET",String.class));
        List<ImportedFixedAsset> updates=new ArrayList<>(),inserts=new ArrayList<>();
        for(ImportedFixedAsset a:rows)(existing.contains(a.machineCode())?updates:inserts).add(a);
        List<String> cols=IMPORT_COLUMNS.stream().filter(rows.get(0)::has).toList();
        if(!updates.isEmpty()) batchUpdate(updates,cols,importedBy);
        if(!inserts.isEmpty()) batchInsert(inserts,cols,importedBy);
    }
    private void batchUpdate(List<ImportedFixedAsset> rows,List<String> cols,String user){
        String assignments=String.join(",",cols.stream().map(c->quoted(c)+"=?").toList());
        String sql="UPDATE dbo.F2_FIXED_ASSET SET "+assignments+(assignments.isEmpty()?"":",")+"UpdateDate=SYSDATETIME(),Updater=? WHERE MachineCode=?";
        batch(sql,rows,(ps,a)->{int i=setValues(ps,a,cols,1);ps.setString(i++,user);ps.setString(i,a.machineCode());});
    }
    private void batchInsert(List<ImportedFixedAsset> rows,List<String> cols,String user){
        List<String> names=new ArrayList<>();names.add("MachineCode");names.addAll(cols);names.add("CreateDate");names.add("Creater");
        String values="?,"+"?,".repeat(cols.size())+"SYSDATETIME(),?";
        String sql="INSERT INTO dbo.F2_FIXED_ASSET ("+String.join(",",names.stream().map(this::quoted).toList())+") VALUES ("+values+")";
        batch(sql,rows,(ps,a)->{ps.setString(1,a.machineCode());int i=setValues(ps,a,cols,2);ps.setString(i,user);});
    }
    private void batch(String sql,List<ImportedFixedAsset> rows,Setter setter){for(int s=0;s<rows.size();s+=BATCH_SIZE){List<ImportedFixedAsset>b=rows.subList(s,Math.min(s+BATCH_SIZE,rows.size()));jdbc.batchUpdate(sql,b,b.size(),setter::set);}}
    private int setValues(PreparedStatement ps,ImportedFixedAsset a,List<String> cols,int i)throws java.sql.SQLException{for(String c:cols)ps.setObject(i++,value(a,c));return i;}
    private Object value(ImportedFixedAsset a,String c){return switch(c){case"FAName"->a.faName();case"PhysicalCount"->a.physicalCount();case"FAType"->a.faType();case"KindExpense"->a.kindExpense();case"KindFixedAsset"->a.kindFixedAsset();case"DateStart"->a.dateStart();case"HistotyCost"->a.histotyCost();case"NetBookValue"->a.netBookValue();case"YearDepreciation"->a.yearDepreciation();case"Group"->a.group();case"EmailChecked"->a.emailChecked();case"EmailApproved"->a.emailApproved();case"Div"->a.div();case"Factory"->a.factory();case"Floor"->a.floor();case"PositionA"->a.positionA();case"PositionAA"->a.positionAA();case"PositionAAA"->a.positionAAA();case"InvoiceNo"->a.invoiceNo();case"Maker"->a.maker();case"PurchasedFrom"->a.purchasedFrom();case"Type"->a.type();case"SerialNo"->a.serialNo();case"CheckMETI"->a.checkMETI();case"Photo1"->a.photo1();case"Photo2"->a.photo2();case"Photo3"->a.photo3();case"PhotoJudge"->a.photoJudge();case"MachineStatus"->a.machineStatus();default->throw new IllegalArgumentException(c);};}
    private String quoted(String c){return c.equals("Group")||c.equals("Type")?"["+c+"]":c;}
    public boolean ping(){Integer one=jdbc.queryForObject("SELECT 1",Integer.class);return one!=null&&one==1;}
    @FunctionalInterface private interface Setter{void set(PreparedStatement ps,ImportedFixedAsset a)throws java.sql.SQLException;}
}
