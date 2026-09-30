package com.spc.fixedasset.controller;

import com.spc.fixedasset.dto.AssetLocationResponse;
import com.spc.fixedasset.dto.LocationResponse;
import com.spc.fixedasset.exception.NotFoundException;
import com.spc.fixedasset.service.LocationMatcher.MatchLevel;
import com.spc.fixedasset.service.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LocationController.class)
class LocationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LocationService service;

    private static AssetLocationResponse sample() {
        return new AssetLocationResponse("M001", "Press 200T", "Machinery", "PRESS", "Active", "PRESS", "2", "2F",
                "A15", "A15-3", "A15-3", 3L, "2", "1F", new BigDecimal("120.5"), "P-1", MatchLevel.SUB, true);
    }

    @Test
    void locationsPassesFiltersAndSerializesPositions() throws Exception {
        when(service.getLocations("2", "PRESS", "1F", "2")).thenReturn(List.of(
                new LocationResponse(2, "2", "PRESS", "1F", "A15", null, new BigDecimal("10"), null, 7)));
        mvc.perform(get("/api/locations").param("fac", "2").param("div", "PRESS").param("floor", "1F").param("assetFactory", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].positionA").value("A15"))
                .andExpect(jsonPath("$[0].positionAA").doesNotExist())
                .andExpect(jsonPath("$[0].aPos").value(10))
                .andExpect(jsonPath("$[0].assetCount").value(7));
    }

    @Test
    void assetsWithLocationReturnsMatchInfo() throws Exception {
        when(service.getAssetsWithLocation("2", null)).thenReturn(List.of(sample()));
        mvc.perform(get("/api/assets/with-location").param("factory", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("M001"))
                .andExpect(jsonPath("$[0].currentZone").value("A15-3"))
                .andExpect(jsonPath("$[0].mapId").value(3))
                .andExpect(jsonPath("$[0].aPos").value(120.5))
                .andExpect(jsonPath("$[0].aaPos").value("P-1"))
                .andExpect(jsonPath("$[0].matchLevel").value("SUB"))
                .andExpect(jsonPath("$[0].floorMismatch").value(true));
    }

    @Test
    void assetLocationReturnsOne() throws Exception {
        when(service.getAssetLocation("M001")).thenReturn(sample());
        mvc.perform(get("/api/assets/M001/location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapFloor").value("1F"));
    }

    @Test
    void assetLocationUnknownCodeReturns404() throws Exception {
        when(service.getAssetLocation("NOPE")).thenThrow(new NotFoundException("Không tìm thấy máy: NOPE"));
        mvc.perform(get("/api/assets/NOPE/location"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Không tìm thấy máy: NOPE"));
    }
}
