package com.spc.fixedasset.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CreaterFormatTest {

    @Test
    void formatJoinsAccountAndCleanedName() {
        assertEquals("22847_Tạ Hoàng Tuấn Việt", CreaterFormat.format(" 22847 ", "  Tạ   Hoàng\tTuấn \n Việt "));
        assertEquals("22847_Unknown", CreaterFormat.format("22847", null));
        assertEquals("22847_Unknown", CreaterFormat.format("22847", "   "));
    }

    @Test
    void formatCutsOnlyTheNameWhenTooLong() {
        assertEquals("22847_Tạ Hoàng", CreaterFormat.format("22847", "Tạ Hoàng Tuấn Việt", 14));
        // Trailing space left by the cut is dropped.
        assertEquals("22847_Tạ", CreaterFormat.format("22847", "Tạ Hoàng", 9));
        assertEquals("22847_", CreaterFormat.format("22847", "Tạ Hoàng", 6));
        assertEquals("22847_Tạ Hoàng Tuấn Việt", CreaterFormat.format("22847", "Tạ Hoàng Tuấn Việt", 50));
        assertEquals("22847_Tạ Hoàng Tuấn Việt", CreaterFormat.format("22847", "Tạ Hoàng Tuấn Việt", -1));
    }

    @Test
    void accountAndNameOfNewAndOldValues() {
        assertEquals("22847", CreaterFormat.accountOf("22847_Tạ Hoàng Tuấn Việt"));
        assertEquals("Tạ Hoàng Tuấn Việt", CreaterFormat.nameOf("22847_Tạ Hoàng Tuấn Việt"));
        assertEquals("22847", CreaterFormat.accountOf("22847_Nguyễn_Văn"));
        assertEquals("Nguyễn_Văn", CreaterFormat.nameOf("22847_Nguyễn_Văn"));
        assertNull(CreaterFormat.nameOf("22847_Unknown"));
        // Old rows: only the account.
        assertEquals("E001", CreaterFormat.accountOf(" E001 "));
        assertNull(CreaterFormat.nameOf("E001"));
        assertNull(CreaterFormat.accountOf(null));
        assertNull(CreaterFormat.nameOf(" "));
    }

    @Test
    void requesterNameUsesHrOnlyForOldValues() {
        Map<String, String> hr = Map.of("E001", "Nguyễn Văn A");
        assertEquals("Nguyễn Văn A", CreaterFormat.requesterName("E001", hr));
        assertEquals("Tạ Hoàng", CreaterFormat.requesterName("E001_Tạ Hoàng", hr));
        assertNull(CreaterFormat.requesterName("E001_Unknown", hr));
        assertEquals(List.of("E001"), CreaterFormat.legacyAccounts(List.of("E001", " E001 ", "22847_Tạ Hoàng")));
    }
}
