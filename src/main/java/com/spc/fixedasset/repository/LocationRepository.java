package com.spc.fixedasset.repository;

import com.spc.fixedasset.model.LocationAsset;
import com.spc.fixedasset.model.LocationMapRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/** Read-only access to F2_FIXED_ASSET_MAP and the location columns of F2_FIXED_ASSET. */
@Repository
public class LocationRepository {
    private static final String ASSET_SELECT = """
        SELECT MachineCode AS code, FAName AS name, KindFixedAsset AS kind, FAType AS faType, MachineStatus AS status,
          Div AS div, Factory AS factory, Floor AS floor, PositionA AS positionA, PositionAA AS positionAA
        FROM dbo.F2_FIXED_ASSET
        """;
    private static final RowMapper<LocationAsset> ASSET_MAPPER = (rs, n) -> new LocationAsset(rs.getString("code"), rs.getString("name"), rs.getString("kind"), rs.getString("faType"), rs.getString("status"), rs.getString("div"), rs.getString("factory"), rs.getString("floor"), rs.getString("positionA"), rs.getString("positionAA"));
    private final JdbcTemplate jdbc;
    public LocationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public List<LocationMapRow> findMapRows(){return jdbc.query("""
        SELECT Id AS id, Fac AS fac, Div AS div, Floor AS floor, A AS a, AA AS aa, A_Pos AS aPos, AA_Pos AS aaPos
        FROM dbo.F2_FIXED_ASSET_MAP ORDER BY Id
        """,(rs,n)->new LocationMapRow(rs.getLong("id"),rs.getString("fac"),rs.getString("div"),rs.getString("floor"),rs.getString("a"),rs.getString("aa"),rs.getString("aPos"),rs.getString("aaPos")));}

    /** factory/div are optional exact filters (trimmed); values are always bound, never concatenated. */
    public List<LocationAsset> findAssets(String factory,String div){
        List<String> where=new ArrayList<>();List<Object> args=new ArrayList<>();
        if(factory!=null){where.add("LTRIM(RTRIM(Factory)) = ?");args.add(factory);}
        if(div!=null){where.add("LTRIM(RTRIM(Div)) = ?");args.add(div);}
        String sql=ASSET_SELECT+(where.isEmpty()?"":"WHERE "+String.join(" AND ",where)+"\n")+"ORDER BY MachineCode";
        return jdbc.query(sql,ASSET_MAPPER,args.toArray());
    }

    public LocationAsset findAssetByCode(String code){List<LocationAsset> x=jdbc.query(ASSET_SELECT+"WHERE MachineCode = ?",ASSET_MAPPER,code);return x.isEmpty()?null:x.get(0);}
}
