package com.spc.fixedasset.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** dbo.HSE_Patrol_Account: reads only the Pass of one account; values are always bound, never concatenated. */
@Repository
public class AuthRepository {
    private final JdbcTemplate jdbc;
    private volatile Boolean passFixedChar;
    public AuthRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    /** Pass of the account (right-trimmed when the column is CHAR/NCHAR, which pads with spaces), or empty. */
    public Optional<String> findPass(String account){
        List<String> x=jdbc.queryForList("SELECT Pass FROM dbo.HSE_Patrol_Account WHERE Account = ?",String.class,account);
        if(x.isEmpty()||x.get(0)==null)return Optional.empty();
        return Optional.of(passFixedChar()?rtrim(x.get(0)):x.get(0));
    }

    public void updateLastLogin(String account){
        jdbc.update("UPDATE dbo.HSE_Patrol_Account SET Last_Login = SYSDATETIME() WHERE Account = ?",account);
    }

    private boolean passFixedChar(){
        Boolean v=passFixedChar;
        if(v==null){
            String type=jdbc.queryForObject("""
                SELECT MAX(DATA_TYPE) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'HSE_Patrol_Account' AND COLUMN_NAME = 'Pass'
                """,String.class);
            v="char".equalsIgnoreCase(type)||"nchar".equalsIgnoreCase(type);
            passFixedChar=v;
        }
        return v;
    }

    static String rtrim(String v){
        int end=v.length();
        while(end>0&&v.charAt(end-1)==' ')end--;
        return v.substring(0,end);
    }
}
