package com.spc.fixedasset.controller;

import com.spc.fixedasset.dto.DrawingUploadResponse;
import com.spc.fixedasset.exception.BadRequestException;
import com.spc.fixedasset.exception.NotFoundException;
import com.spc.fixedasset.exception.ServiceUnavailableException;
import com.spc.fixedasset.service.RelocationDrawingService;
import com.spc.fixedasset.auth.LoggedInMockMvc;
import com.spc.fixedasset.service.RelocationRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RelocationDrawingController.class)
@Import(LoggedInMockMvc.class)
class RelocationDrawingControllerTest {

    private static final MockMultipartFile FILE = new MockMultipartFile("file", "d.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RelocationDrawingService service;

    @MockitoBean
    private RelocationRequestService requests;

    @BeforeEach
    void creatorIsTheLoggedInAccount() {
        when(requests.creatorOf("RL-2026-0001")).thenReturn(" " + LoggedInMockMvc.ACCOUNT.toLowerCase() + " ");
    }

    @Test
    void otherAccountIs403() throws Exception {
        when(requests.creatorOf("RL-2026-0001")).thenReturn("OTHER");
        mvc.perform(multipart("/api/relocation-requests/RL-2026-0001/drawing").file(FILE))
                .andExpect(status().isForbidden());
        verify(service, never()).upload(any(), any());
    }

    @Test
    void uploadReturns200() throws Exception {
        when(service.upload(eq("RL-2026-0001"), any())).thenReturn(new DrawingUploadResponse("261001-093005_RL-2026-0001_Fac_B_A15-3.png", null));
        mvc.perform(multipart("/api/relocation-requests/RL-2026-0001/drawing").file(FILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("261001-093005_RL-2026-0001_Fac_B_A15-3.png"));
    }

    @Test
    void missingFileIs400() throws Exception {
        mvc.perform(multipart("/api/relocation-requests/RL-2026-0001/drawing"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notPngIs400() throws Exception {
        when(service.upload(eq("RL-2026-0001"), any())).thenThrow(new BadRequestException("Bản vẽ phải là file PNG."));
        mvc.perform(multipart("/api/relocation-requests/RL-2026-0001/drawing").file(FILE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bản vẽ phải là file PNG."));
    }

    @Test
    void unknownRequestIs404() throws Exception {
        when(requests.creatorOf("RL-2026-9999")).thenThrow(new NotFoundException("Không tìm thấy yêu cầu di dời: RL-2026-9999"));
        mvc.perform(multipart("/api/relocation-requests/RL-2026-9999/drawing").file(FILE))
                .andExpect(status().isNotFound());
    }

    @Test
    void storageUnavailableIs503() throws Exception {
        when(service.upload(eq("RL-2026-0001"), any())).thenThrow(new ServiceUnavailableException("Chưa cấu hình drawings.dir."));
        mvc.perform(multipart("/api/relocation-requests/RL-2026-0001/drawing").file(FILE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Chưa cấu hình drawings.dir."));
    }
}
