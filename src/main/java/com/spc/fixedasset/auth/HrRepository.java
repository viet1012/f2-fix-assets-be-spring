package com.spc.fixedasset.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/** dbo.F2_HR_Data, read-only: the best row for one employee code (active first, then newest). */
@Repository
public class HrRepository {
    private final JdbcTemplate jdbc;
    public HrRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Optional<HrProfile> findProfile(String account){
        List<HrProfile> x=jdbc.query("""
            SELECT TOP 1 Name, Dept, Section, Date_of_resign FROM dbo.F2_HR_Data
            WHERE LTRIM(RTRIM(Code)) = ?
            ORDER BY CASE WHEN Date_of_resign IS NULL OR Date_of_resign > CAST(GETDATE() AS date) THEN 0 ELSE 1 END, reg_date DESC, id DESC
            """,(rs,n)->new HrProfile(trim(rs.getString(1)),trim(rs.getString(2)),trim(rs.getString(3)),resigned(rs.getDate(4),LocalDate.now())),account);
        return x.stream().findFirst();
    }

    /**
     * Name per code for many codes in one query (same row choice as findProfile: active first, newest reg_date, id).
     * Keys are the trimmed codes, looked up case-insensitively; codes without HR data are absent.
     */
    public Map<String,String> findNames(Collection<String> codes){
        Map<String,String> names=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<String> list=codes.stream().filter(Objects::nonNull).map(String::trim).filter(c->!c.isEmpty()).distinct().toList();
        if(list.isEmpty())return names;
        jdbc.query("""
            SELECT Code, Name FROM (
              SELECT LTRIM(RTRIM(Code)) AS Code, Name, ROW_NUMBER() OVER (PARTITION BY LTRIM(RTRIM(Code))
                ORDER BY CASE WHEN Date_of_resign IS NULL OR Date_of_resign > CAST(GETDATE() AS date) THEN 0 ELSE 1 END, reg_date DESC, id DESC) AS rn
              FROM dbo.F2_HR_Data WHERE LTRIM(RTRIM(Code)) IN (%s)
            ) x WHERE rn = 1
            """.formatted(String.join(",",Collections.nCopies(list.size(),"?"))),
            rs->{String n=trim(rs.getString(2));if(n!=null)names.put(rs.getString(1),n);},list.toArray());
        return names;
    }

    /** Same rule as the ORDER BY: resigned when the date is set and not after today. */
    static boolean resigned(Date dateOfResign,LocalDate today){
        return dateOfResign!=null&&!dateOfResign.toLocalDate().isAfter(today);
    }

    private static String trim(String v){
        if(v==null)return null;
        String t=v.trim();
        return t.isEmpty()?null:t;
    }
}
