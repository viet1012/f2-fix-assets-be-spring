package com.spc.fixedasset.service;

import com.spc.fixedasset.dto.AssetLocationResponse;
import com.spc.fixedasset.dto.LocationResponse;
import com.spc.fixedasset.exception.NotFoundException;
import com.spc.fixedasset.model.LocationAsset;
import com.spc.fixedasset.model.LocationMapRow;
import com.spc.fixedasset.repository.LocationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocationServiceTest {

    private final LocationRepository repo = mock(LocationRepository.class);
    private final LocationService service = new LocationService(repo);

    private static LocationAsset asset(String code, String a, String aa, String floor) {
        return new LocationAsset(code, "n", "Machinery", "T", "Active", "PRESS", "2", floor, a, aa);
    }

    @Test
    void majorAssetCountIncludesSubZones() {
        when(repo.findMapRows()).thenReturn(List.of(
                new LocationMapRow(1, "2", "PRESS", "1F", "A-15", null, "10", "x"),
                new LocationMapRow(2, "2", "PRESS", "1F", "A15", "A-15-3", null, null),
                new LocationMapRow(3, "2", "PRESS", "2F", "B1", null, null, null)));
        when(repo.findAssets("2", null)).thenReturn(List.of(
                asset("M1", "A15", null, "1F"), asset("M2", "A-15", "A-15-3", "1F"), asset("M3", "Z9", null, "1F")));

        List<LocationResponse> all = service.getLocations(null, null, null, " 2 ");
        assertEquals(2, all.get(0).assetCount());
        assertEquals(1, all.get(1).assetCount());
        assertEquals("A15-3", all.get(1).positionAA());
        assertEquals(List.of(3L), service.getLocations(null, null, "2f", "2").stream().map(LocationResponse::id).toList());
    }

    @Test
    void assetLocationThrowsNotFound() {
        when(repo.findAssetByCode("X")).thenReturn(null);
        assertThrows(NotFoundException.class, () -> service.getAssetLocation("X"));
    }

    @Test
    void assetLocationFlagsFloorMismatch() {
        when(repo.findAssetByCode("M1")).thenReturn(asset("M1", "A-15", "A-15-3", "2F"));
        when(repo.findMapRows()).thenReturn(List.of(new LocationMapRow(2, "2", "PRESS", "1F", "A15", "A15-3", "5", null)));
        AssetLocationResponse r = service.getAssetLocation("M1");
        assertEquals(LocationMatcher.MatchLevel.SUB, r.matchLevel());
        assertTrue(r.floorMismatch());
        assertEquals("1F", r.mapFloor());
    }

    @Test
    void assetsWithLocationFiltersOnMatchedMapFac() {
        when(repo.findMapRows()).thenReturn(List.of(
                new LocationMapRow(1, "Fac_A", "PRESS", "1F", "A15", "A15-3", null, null),
                new LocationMapRow(2, "Fac_B", "PRESS", "1F", "B1", null, null, null)));
        when(repo.findAssets("Factory 2", null)).thenReturn(List.of(
                asset("M1", "A15", "A15-3", "1F"), asset("M2", "B1", null, "1F"), asset("M3", "Z9", null, "1F")));

        assertEquals(List.of("M1", "M2", "M3"), codes(service.getAssetsWithLocation(" Factory 2 ", null, null)));
        assertEquals(List.of("M1", "M2", "M3"), codes(service.getAssetsWithLocation("Factory 2", null, "  ")));
        assertEquals(List.of("M1"), codes(service.getAssetsWithLocation("Factory 2", null, " fac_a ")));
        assertEquals(List.of("M2"), codes(service.getAssetsWithLocation("Factory 2", null, "Fac_B")));
        assertEquals(List.of(), codes(service.getAssetsWithLocation("Factory 2", null, "Fac_C")));
        verify(repo, atLeastOnce()).findAssets("Factory 2", null);
    }

    private static List<String> codes(List<AssetLocationResponse> rows) {
        return rows.stream().map(AssetLocationResponse::code).toList();
    }
}
