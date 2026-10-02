package com.spc.fixedasset.auth;

import com.spc.fixedasset.controller.FixedAssetController;
import com.spc.fixedasset.controller.RelocationRequestController;
import com.spc.fixedasset.service.FixedAssetService;
import com.spc.fixedasset.service.RelocationRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** No LoggedInMockMvc here: requests have no session unless a test adds one. */
@WebMvcTest({RelocationRequestController.class, FixedAssetController.class})
class AuthInterceptorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RelocationRequestService service;

    @MockitoBean
    private FixedAssetService fixedAssets;

    @Test
    void apiWithoutLoginIs401AndNeverReachesTheController() throws Exception {
        mvc.perform(get("/api/relocation-requests")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Chưa đăng nhập"));
        mvc.perform(post("/api/relocation-requests").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/assets")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, fixedAssets);
    }

    @Test
    void loggedInSessionPasses() throws Exception {
        mvc.perform(get("/api/relocation-requests").sessionAttr(AuthSession.ACCOUNT, "E001")).andExpect(status().isOk());
        verify(service).list(any(), any(), any(), any(), any());
    }

    @Test
    void healthAndPreflightAreOpen() throws Exception {
        when(fixedAssets.health()).thenThrow(new IllegalStateException("db down"));
        mvc.perform(get("/api/health")).andExpect(r -> assertNotEquals(401, r.getResponse().getStatus()));
        mvc.perform(options("/api/relocation-requests")).andExpect(r -> assertNotEquals(401, r.getResponse().getStatus()));
    }
}
