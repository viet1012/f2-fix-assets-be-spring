package com.spc.fixedasset.controller;

import com.spc.fixedasset.dto.*;
import com.spc.fixedasset.exception.BadRequestException;
import com.spc.fixedasset.exception.ConflictException;
import com.spc.fixedasset.exception.NotFoundException;
import com.spc.fixedasset.service.RelocationRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RelocationRequestController.class)
class RelocationRequestControllerTest {

    private static final String BODY = """
        {"machineCodes":["M1","M2"],"to":{"positionA":"A15","positionAA":"A15-3"},
         "plannedMoveDate":"2026-10-05","plannedDoneDate":"2026-10-06","reason":"Layout change","requestedBy":"E001"}
        """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RelocationRequestService service;

    @Test
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(new RelocationCreateResponse("RL-2026-0001", "REQ_PENDING",
                List.of(new RelocationCreateResponse.Item("M1", new RelocationPosition("A2", "A2-3", null), new RelocationPosition("A15", "A15-3", null), "building")),
                List.of("M2")));
        mvc.perform(post("/api/relocation-requests").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestNo").value("RL-2026-0001"))
                .andExpect(jsonPath("$.items[0].from.positionAA").value("A2-3"))
                .andExpect(jsonPath("$.items[0].moveType").value("building"))
                .andExpect(jsonPath("$.skipped[0]").value("M2"));
    }

    @Test
    void createValidationErrorReturns400() throws Exception {
        when(service.create(any())).thenThrow(new BadRequestException("reason là bắt buộc."));
        mvc.perform(post("/api/relocation-requests").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("reason là bắt buộc."));
    }

    @Test
    void malformedDateReturns400() throws Exception {
        mvc.perform(post("/api/relocation-requests").contentType(MediaType.APPLICATION_JSON).content(BODY.replace("2026-10-05", "05/10/2026")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openRequestReturns409WithCodes() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("Máy đang có yêu cầu di dời chưa xử lý: M1", List.of("M1")));
        mvc.perform(post("/api/relocation-requests").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codes[0]").value("M1"));
    }

    @Test
    void listPassesFilters() throws Exception {
        RelocationRequestResponse r = new RelocationRequestResponse("RL-2026-0001", "REQ_PENDING", "E001", "x",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), LocalDateTime.of(2026, 10, 1, 9, 30),
                new RelocationPosition("A15", "A15-3", null), "https://x.sharepoint.com/d.png", List.of());
        when(service.list("REQ_PENDING", "M1", "E001", 0, 10)).thenReturn(new RelocationRequestPage(List.of(r), 0, 10, 1));
        mvc.perform(get("/api/relocation-requests").param("status", "REQ_PENDING").param("machineCode", "M1")
                        .param("requestedBy", "E001").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].plannedMoveDate").value("2026-10-05"))
                .andExpect(jsonPath("$.items[0].drawingUrl").value("https://x.sharepoint.com/d.png"));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        when(service.get("RL-2026-9999")).thenThrow(new NotFoundException("Không tìm thấy yêu cầu di dời: RL-2026-9999"));
        mvc.perform(get("/api/relocation-requests/RL-2026-9999"))
                .andExpect(status().isNotFound());
    }
}
