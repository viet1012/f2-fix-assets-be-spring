package com.spc.fixedasset.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelocationFileNamesTest {

    @Test
    void baseNameIsRequestNoAndHoChiMinhCreateTime() {
        assertEquals("R0001_261006-091432",
                RelocationFileNames.baseName("R0001", LocalDateTime.of(2026, 10, 6, 9, 14, 32), ZoneId.of("Asia/Ho_Chi_Minh")));
        // 20:15 UTC on 31 Dec is already 1 Jan in Vietnam (UTC+7).
        assertEquals("R0042_270101-031500", RelocationFileNames.baseName("R0042", LocalDateTime.of(2026, 12, 31, 20, 15), ZoneOffset.UTC));
    }

    @Test
    void reservedCharactersAreReplaced() {
        assertEquals("Fac-A-1-2-3-4-5-6-7-8-9-10", RelocationFileNames.part("Fac\\A/1:2*3?4\"5<6>7|8#9%10"));
        assertEquals("A-15", RelocationFileNames.part(" A 15. "));
        assertEquals("NA", RelocationFileNames.part(null));
        assertEquals("NA", RelocationFileNames.part(".."));
    }
}
