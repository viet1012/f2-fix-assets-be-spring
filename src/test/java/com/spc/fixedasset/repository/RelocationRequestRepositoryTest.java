package com.spc.fixedasset.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RelocationRequestRepositoryTest {

    @Test
    void maxRequestSeqReadsOnlyNewRCodesUnderLock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.queryForObject(sql.capture(), eq(Integer.class))).thenReturn(41);

        assertEquals(41, new RelocationRequestRepository(jdbc).maxRequestSeq());

        String q = sql.getValue();
        // 'R[0-9]%' excludes the old "RL-YYYY-NNNN" codes (the 2nd character is a letter, not a digit).
        assertTrue(q.contains("RequestNo LIKE 'R[0-9]%'"), q);
        assertTrue(q.contains("TRY_CAST(SUBSTRING(RequestNo, 2, 10) AS int)"), q);
        assertTrue(q.contains("WITH (UPDLOCK, HOLDLOCK)"), q);
        assertTrue(q.contains("Status LIKE 'REQ[_]%'"), q);
    }

    @Test
    void requestedByMatchesOldAndNewCreater() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new RelocationRequestRepository(jdbc).countRequests(new RelocationRequestRepository.Filter(null, null, "E_1%"));

        Object[] raw = mockingDetails(jdbc).getInvocations().iterator().next().getRawArguments();
        String sql = (String) raw[0];
        assertTrue(sql.contains("(LTRIM(RTRIM(Creater)) = ? OR Creater LIKE ? ESCAPE '\\')"), sql);
        // Old rows: the bare account; new rows: "{account}_…" with LIKE wildcards in the account taken literally.
        assertEquals(java.util.List.of("E_1%", "E\\_1\\%\\_%"), java.util.List.of((Object[]) raw[2]));
    }

    @Test
    void escapeLikeEscapesWildcardsAndBackslash() {
        assertEquals("a\\%b\\_c\\[d\\\\e", RelocationRequestRepository.escapeLike("a%b_c[d\\e"));
    }
}
