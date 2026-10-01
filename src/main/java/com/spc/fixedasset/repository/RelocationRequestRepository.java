package com.spc.fixedasset.repository;

import com.spc.fixedasset.model.HistoryTableInfo;
import com.spc.fixedasset.model.RelocationAsset;
import com.spc.fixedasset.model.RelocationHistoryRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

/**
 * Relocation requests in dbo.F2_FIXED_ASSET_HISTORY. Every query is limited to Status LIKE 'REQ[_]%' so rows written by
 * other features are never read or changed. Values are always bound, never concatenated.
 */
@Repository
public class RelocationRequestRepository {
    public static final String TABLE = "dbo.F2_FIXED_ASSET_HISTORY";
    /** Status prefix of this feature ("_" escaped for LIKE). */
    private static final String REQ = "Status LIKE 'REQ[_]%'";
    private static final String ROW_SELECT = """
        SELECT Id, RequestNo, MachineCode, PositionA_BF, PositionAA_BF, PositionAAA_BF, Group_BF, PIC_BF,
          PositionA_AT, PositionAA_AT, PositionAAA_AT, Group_AT, PIC_AT, PlannedMoveDate, PlannedDoneDate,
          CreateDate, Creater, Note, Status, Drawings
        FROM dbo.F2_FIXED_ASSET_HISTORY
        """;
    private static final RowMapper<RelocationHistoryRow> ROW_MAPPER = (rs, n) -> new RelocationHistoryRow(
            rs.getLong("Id"), rs.getString("RequestNo"), rs.getString("MachineCode"),
            rs.getString("PositionA_BF"), rs.getString("PositionAA_BF"), rs.getString("PositionAAA_BF"), rs.getString("Group_BF"), rs.getString("PIC_BF"),
            rs.getString("PositionA_AT"), rs.getString("PositionAA_AT"), rs.getString("PositionAAA_AT"), rs.getString("Group_AT"), rs.getString("PIC_AT"),
            localDate(rs.getDate("PlannedMoveDate")), localDate(rs.getDate("PlannedDoneDate")),
            rs.getTimestamp("CreateDate") == null ? null : rs.getTimestamp("CreateDate").toLocalDateTime(),
            rs.getString("Creater"), rs.getString("Note"), rs.getString("Status"), rs.getString("Drawings"));

    public record Filter(String status, String machineCode, String requestedBy) {}

    private final JdbcTemplate jdbc;
    private volatile HistoryTableInfo tableInfo;
    public RelocationRequestRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    /** Read once from the catalog: lengths drive validation, IsIdentity decides how Id is generated. */
    public HistoryTableInfo tableInfo(){
        HistoryTableInfo info=tableInfo;
        if(info!=null)return info;
        Map<String,Integer> lengths=new HashMap<>();
        jdbc.query("""
            SELECT COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'F2_FIXED_ASSET_HISTORY' AND CHARACTER_MAXIMUM_LENGTH IS NOT NULL
            """,rs->{lengths.put(rs.getString(1).toLowerCase(Locale.ROOT),rs.getInt(2));});
        Integer identity=jdbc.queryForObject("SELECT COLUMNPROPERTY(OBJECT_ID(N'dbo.F2_FIXED_ASSET_HISTORY'), 'Id', 'IsIdentity')",Integer.class);
        info=new HistoryTableInfo(Map.copyOf(lengths),identity!=null&&identity==1);
        tableInfo=info;
        return info;
    }

    public List<RelocationAsset> findAssets(Collection<String> codes){
        if(codes.isEmpty())return List.of();
        return jdbc.query("""
            SELECT MachineCode, KindFixedAsset, Div, Floor, PositionA, PositionAA, PositionAAA, [Group], EmailChecked
            FROM dbo.F2_FIXED_ASSET WHERE MachineCode IN (%s)
            """.formatted(placeholders(codes.size())),
            (rs,n)->new RelocationAsset(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9)),
            codes.toArray());
    }

    /** Codes that already have a row in one of the open statuses; UPDLOCK+HOLDLOCK keeps the range locked until commit. */
    public List<String> findOpenMachineCodes(Collection<String> codes,Collection<String> openStatuses){
        if(codes.isEmpty())return List.of();
        List<Object> args=new ArrayList<>(codes);args.addAll(openStatuses);
        return jdbc.queryForList("""
            SELECT DISTINCT MachineCode FROM dbo.F2_FIXED_ASSET_HISTORY WITH (UPDLOCK, HOLDLOCK)
            WHERE MachineCode IN (%s) AND Status IN (%s)
            """.formatted(placeholders(codes.size()),placeholders(openStatuses.size())),String.class,args.toArray());
    }

    /** Highest nnnn of RL-{year}-nnnn, or null; the lock serializes concurrent creators until commit. */
    public Integer maxRequestSeq(int year){
        return jdbc.queryForObject("""
            SELECT MAX(TRY_CAST(SUBSTRING(RequestNo, 9, 20) AS int)) FROM dbo.F2_FIXED_ASSET_HISTORY WITH (UPDLOCK, HOLDLOCK)
            WHERE RequestNo LIKE ? AND %s
            """.formatted(REQ),Integer.class,"RL-"+year+"-%");
    }

    /** Only used when Id is not IDENTITY: every row of the table counts, not just REQ_ rows. */
    public long maxId(){
        Long max=jdbc.queryForObject("SELECT MAX(CAST(Id AS bigint)) FROM dbo.F2_FIXED_ASSET_HISTORY WITH (UPDLOCK, HOLDLOCK)",Long.class);
        return max==null?0:max;
    }

    /** withId: rows carry their Id (table without IDENTITY); otherwise the database generates it. */
    public void insertAll(List<RelocationHistoryRow> rows,boolean withId){
        String cols="RequestNo,MachineCode,PositionA_BF,PositionAA_BF,PositionAAA_BF,Group_BF,PIC_BF,PositionA_AT,PositionAA_AT,PositionAAA_AT,Group_AT,PIC_AT,PlannedMoveDate,PlannedDoneDate,CreateDate,Creater,Note,Status";
        int n=18+(withId?1:0);
        String sql="INSERT INTO dbo.F2_FIXED_ASSET_HISTORY ("+(withId?"Id,":"")+cols+") VALUES ("+placeholders(n)+")";
        jdbc.batchUpdate(sql,rows.stream().map(r->{
            List<Object> v=new ArrayList<>(n);
            if(withId)v.add(r.id());
            Collections.addAll(v,r.requestNo(),r.machineCode(),r.positionABf(),r.positionAABf(),r.positionAAABf(),r.groupBf(),r.picBf(),
                    r.positionAAt(),r.positionAAAt(),r.positionAAAAt(),r.groupAt(),r.picAt(),
                    Date.valueOf(r.plannedMoveDate()),Date.valueOf(r.plannedDoneDate()),Timestamp.valueOf(r.createDate()),r.creater(),r.note(),r.status());
            return v.toArray();
        }).toList());
    }

    /** RequestNos of one page, newest first. */
    public List<String> findRequestNos(Filter f,int offset,int size){
        List<Object> args=new ArrayList<>();
        String sql="SELECT RequestNo FROM dbo.F2_FIXED_ASSET_HISTORY WHERE "+where(f,args)
                +" GROUP BY RequestNo ORDER BY MAX(CreateDate) DESC, RequestNo DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        args.add(offset);args.add(size);
        return jdbc.queryForList(sql,String.class,args.toArray());
    }

    public long countRequests(Filter f){
        List<Object> args=new ArrayList<>();
        Long c=jdbc.queryForObject("SELECT COUNT(DISTINCT RequestNo) FROM dbo.F2_FIXED_ASSET_HISTORY WHERE "+where(f,args),Long.class,args.toArray());
        return c==null?0:c;
    }

    public List<RelocationHistoryRow> findRows(Collection<String> requestNos){
        if(requestNos.isEmpty())return List.of();
        return jdbc.query(ROW_SELECT+"WHERE RequestNo IN ("+placeholders(requestNos.size())+") AND "+REQ+"\nORDER BY RequestNo, MachineCode",ROW_MAPPER,requestNos.toArray());
    }

    /** Sets Drawings on every REQ_ row of the request; returns the number of rows updated. */
    public int updateDrawings(String requestNo,String drawings){
        return jdbc.update("UPDATE dbo.F2_FIXED_ASSET_HISTORY SET Drawings = ? WHERE RequestNo = ? AND "+REQ,drawings,requestNo);
    }

    private static String where(Filter f,List<Object> args){
        List<String> w=new ArrayList<>(List.of(REQ,"RequestNo IS NOT NULL"));
        if(f.status()!=null){w.add("Status = ?");args.add(f.status());}
        if(f.requestedBy()!=null){w.add("LTRIM(RTRIM(Creater)) = ?");args.add(f.requestedBy());}
        if(f.machineCode()!=null){w.add("RequestNo IN (SELECT RequestNo FROM dbo.F2_FIXED_ASSET_HISTORY WHERE MachineCode = ? AND "+REQ+")");args.add(f.machineCode());}
        return String.join(" AND ",w);
    }

    private static String placeholders(int n){return String.join(",",Collections.nCopies(n,"?"));}
    private static LocalDate localDate(Date d){return d==null?null:d.toLocalDate();}
}
