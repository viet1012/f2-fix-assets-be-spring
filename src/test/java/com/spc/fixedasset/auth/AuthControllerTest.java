package com.spc.fixedasset.auth;

import com.spc.fixedasset.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real AuthService + controller; only the database is mocked. */
@WebMvcTest(AuthController.class)
@Import(AuthService.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTest {

    private static final String SECRET = "Secr3t!-xyz";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthRepository repo;

    @MockitoBean
    private HrRepository hr;

    @BeforeEach
    void setUp() {
        when(repo.findPass("E001")).thenReturn(Optional.of(SECRET));
        when(repo.findPass("NOPE")).thenReturn(Optional.empty());
        when(hr.findProfile("E001")).thenReturn(Optional.of(new HrProfile("Nguyễn Văn A", "PE", "Press", false)));
    }

    private static String body(String account, String password) {
        return "{\"account\":" + (account == null ? "null" : "\"" + account + "\"") + ",\"password\":" + (password == null ? "null" : "\"" + password + "\"") + "}";
    }

    private MvcResult login(String account, String password, MockHttpSession session) throws Exception {
        var req = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(account, password));
        return mvc.perform(session == null ? req : req.session(session)).andReturn();
    }

    @Test
    void loginReplacesTheSessionAndStoresTheTrimmedAccount() throws Exception {
        MockHttpSession old = new MockHttpSession();
        old.setAttribute("x", "y");
        MvcResult r = login("  E001 ", SECRET, old);
        assertEquals(200, r.getResponse().getStatus());
        assertEquals("{\"account\":\"E001\",\"name\":\"Nguyễn Văn A\",\"dept\":\"PE\",\"section\":\"Press\"}", r.getResponse().getContentAsString());
        assertTrue(old.isInvalid());
        MockHttpSession now = (MockHttpSession) r.getRequest().getSession(false);
        assertNotNull(now);
        assertNotEquals(old.getId(), now.getId());
        assertEquals("E001", now.getAttribute(AuthSession.ACCOUNT));
        assertNull(now.getAttribute("x"));
        assertEquals(8 * 3600, now.getMaxInactiveInterval());

        mvc.perform(get("/api/auth/me").session(now)).andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("E001"))
                .andExpect(jsonPath("$.name").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.dept").value("PE"))
                .andExpect(jsonPath("$.section").value("Press"))
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(post("/api/auth/logout").session(now)).andExpect(status().isNoContent());
        assertTrue(now.isInvalid());
    }

    @Test
    void wrongPasswordOrUnknownAccountIs401WithTheSameBody(CapturedOutput output) throws Exception {
        MvcResult wrong = login("E001", "secr3t!-xyz", null);
        MvcResult unknown = login("NOPE", SECRET, null);
        assertEquals(401, wrong.getResponse().getStatus());
        assertEquals(401, unknown.getResponse().getStatus());
        assertEquals("{\"error\":\"Sai tài khoản hoặc mật khẩu\"}", wrong.getResponse().getContentAsString());
        assertEquals(wrong.getResponse().getContentAsString(), unknown.getResponse().getContentAsString());
        assertNull(wrong.getRequest().getSession(false));
        assertFalse(output.getAll().contains(SECRET));
        assertFalse(output.getAll().contains("secr3t!-xyz"));
    }

    @Test
    void missingFieldsAre400() throws Exception {
        assertEquals(400, login(" ", SECRET, null).getResponse().getStatus());
        assertEquals(400, login("E001", null, null).getResponse().getStatus());
        assertEquals(400, login("E001", "", null).getResponse().getStatus());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutHrProfileLoginIs200WithNullName() throws Exception {
        when(hr.findProfile("E001")).thenReturn(Optional.empty());
        MvcResult r = login("E001", SECRET, null);
        assertEquals(200, r.getResponse().getStatus());
        assertEquals("{\"account\":\"E001\",\"name\":null,\"dept\":null,\"section\":null}", r.getResponse().getContentAsString());
    }

    @Test
    void resignedEmployeeIs401WithTheUsualMessage() throws Exception {
        when(hr.findProfile("E001")).thenReturn(Optional.of(new HrProfile("A", "PE", "Press", true)));
        MvcResult r = login("E001", SECRET, null);
        assertEquals(401, r.getResponse().getStatus());
        assertEquals("{\"error\":\"Sai tài khoản hoặc mật khẩu\"}", r.getResponse().getContentAsString());
        assertNull(r.getRequest().getSession(false));
    }

    @Test
    void meWithoutSessionIs401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRequestToStringHidesThePassword() {
        assertFalse(new LoginRequest("E001", SECRET).toString().contains(SECRET));
    }
}
