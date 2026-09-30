package com.spc.fixedasset.service;

import com.spc.fixedasset.model.LocationMapRow;
import com.spc.fixedasset.service.LocationMatcher.Match;
import com.spc.fixedasset.service.LocationMatcher.MatchLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocationMatcherTest {

    private static LocationMapRow row(long id, String div, String floor, String a, String aa) {
        return new LocationMapRow(id, "2", div, floor, a, aa, "120.5", "P-1");
    }

    private static final List<LocationMapRow> MAP = List.of(
            row(1, "PRESS", "1F", "A9", null),
            row(2, "PRESS", "1F", "A15", null),
            row(3, "PRESS", "1F", "A15", "A15-3"),
            row(4, "GUIDE", "2F", "B2", "B2-1"),
            row(5, "MOLD", "2F", "B2", "B2-1"),
            row(6, "PRESS", "1F", "C1", "C1"));

    @Test
    void normalizesTrimUpperAndPrefixHyphen() {
        assertEquals("A15-3", LocationMatcher.normalize(" a-15-3 "));
        assertEquals("A15", LocationMatcher.normalize("A-15"));
        assertNull(LocationMatcher.normalize("   "));
        assertNull(LocationMatcher.normalizeSub("A9", " a9 "));
    }

    @Test
    void a9a9MatchesMajorZone() {
        Match m = LocationMatcher.match("A9", "A9", "PRESS", MAP);
        assertEquals(MatchLevel.MAJOR, m.level());
        assertEquals(1, m.row().id());
        assertEquals("A9", LocationMatcher.currentZone("A9", "A9", m));
    }

    @Test
    void hyphenatedSubZoneMatchesSub() {
        Match m = LocationMatcher.match("A-15", "A-15-3", "PRESS", MAP);
        assertEquals(MatchLevel.SUB, m.level());
        assertEquals(3, m.row().id());
        assertEquals("A15-3", LocationMatcher.currentZone("A-15", "A-15-3", m));
    }

    @Test
    void unknownSubZoneFallsBackToMajor() {
        Match m = LocationMatcher.match("A-15", "A-15-9", "PRESS", MAP);
        assertEquals(MatchLevel.MAJOR, m.level());
        assertEquals(2, m.row().id());
    }

    @Test
    void mapRowWithAaEqualToAIsMajor() {
        Match m = LocationMatcher.match("C1", null, "PRESS", MAP);
        assertEquals(MatchLevel.MAJOR, m.level());
        assertEquals(6, m.row().id());
    }

    @Test
    void duplicateAaPrefersSameDivThenLowestId() {
        assertEquals(5, LocationMatcher.match("B2", "B2-1", "mold", MAP).row().id());
        assertEquals(4, LocationMatcher.match("B2", "B2-1", "OTHER", MAP).row().id());
    }

    @Test
    void duplicateFacAAaRowsPreferAssetFloorThenLowestId() {
        List<LocationMapRow> dup = List.of(
                new LocationMapRow(7, "Fac_A", "PRESS", "1F", "D1", "D1-1", null, null),
                new LocationMapRow(8, "Fac_A", "PRESS", "2F", "D1", "D1-1", null, null),
                new LocationMapRow(9, "Fac_A", "PRESS", "2F", "D1", "D1-1", null, null),
                new LocationMapRow(10, "Fac_A", "PRESS", "2F", "D1", null, null, null),
                new LocationMapRow(11, "Fac_A", "PRESS", "1F", "D1", null, null, null));
        assertEquals(8, LocationMatcher.match("D1", "D1-1", "PRESS", " 2f ", dup).row().id());
        assertEquals(7, LocationMatcher.match("D1", "D1-1", "PRESS", "1F", dup).row().id());
        assertEquals(7, LocationMatcher.match("D1", "D1-1", "PRESS", "3F", dup).row().id());
        assertEquals(7, LocationMatcher.match("D1", "D1-1", "PRESS", null, dup).row().id());
        assertEquals(11, LocationMatcher.match("D1", null, "PRESS", "1F", dup).row().id());
        assertFalse(LocationMatcher.floorMismatch("2F", LocationMatcher.match("D1", "D1-1", "PRESS", "2F", dup)));
    }

    @Test
    void noMatchReturnsNone() {
        Match m = LocationMatcher.match("Z99", "Z99-1", "PRESS", MAP);
        assertEquals(MatchLevel.NONE, m.level());
        assertNull(m.row());
        assertEquals("Z99-1", LocationMatcher.currentZone("Z99", "Z99-1", m));
        assertFalse(LocationMatcher.floorMismatch("1F", m));
        assertEquals(MatchLevel.NONE, LocationMatcher.match(null, null, "PRESS", MAP).level());
    }

    @Test
    void floorMismatchWhenFloorsDifferButStillMatched() {
        Match m = LocationMatcher.match("A15", "A15-3", "PRESS", MAP);
        assertEquals(MatchLevel.SUB, m.level());
        assertTrue(LocationMatcher.floorMismatch("2F", m));
        assertFalse(LocationMatcher.floorMismatch(" 1f ", m));
        assertFalse(LocationMatcher.floorMismatch(null, m));
    }

    @Test
    void parsePosReturnsNumberOrOriginalString() {
        assertEquals(new BigDecimal("120.5"), LocationMatcher.parsePos(" 120.5 "));
        assertEquals("P-1", LocationMatcher.parsePos("P-1"));
        assertNull(LocationMatcher.parsePos(" "));
    }
}
