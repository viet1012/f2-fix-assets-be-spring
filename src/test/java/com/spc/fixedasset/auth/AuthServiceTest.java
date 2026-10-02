package com.spc.fixedasset.auth;

import com.spc.fixedasset.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final AuthRepository repo = mock(AuthRepository.class);
    private final HrRepository hr = mock(HrRepository.class);
    private final AuthService service = new AuthService(repo, hr, false, true);

    @BeforeEach
    void setUp() {
        when(repo.findPass("E001")).thenReturn(Optional.of("Secr3t!"));
        when(repo.findPass("NOPE")).thenReturn(Optional.empty());
        when(hr.findProfile("E001")).thenReturn(Optional.of(new HrProfile("Nguyễn Văn A", "PE", "Press", false)));
    }

    private void fail(String account, String password) {
        assertThrows(UnauthorizedException.class, () -> service.login(account, password));
    }

    @Test
    void correctPasswordLogsIn() {
        assertEquals(new CurrentUser("E001", "Nguyễn Văn A", "PE", "Press"), service.login("E001", "Secr3t!"));
        verify(repo, never()).updateLastLogin(anyString());
    }

    @Test
    void wrongPasswordCaseOrSpacesAreRejectedWithTheSameMessage() {
        UnauthorizedException wrong = assertThrows(UnauthorizedException.class, () -> service.login("E001", "nope"));
        UnauthorizedException unknown = assertThrows(UnauthorizedException.class, () -> service.login("NOPE", "Secr3t!"));
        assertEquals("Sai tài khoản hoặc mật khẩu", wrong.getMessage());
        assertEquals(wrong.getMessage(), unknown.getMessage());
        fail("E001", "secr3t!");
        fail("E001", "Secr3t! ");
        fail("E001", " Secr3t!");
        fail("E001", "");
    }

    @Test
    void unknownAccountNeverMatchesEvenAnEmptyOrRandomPassword() {
        fail("NOPE", "");
        fail("NOPE", "x".repeat(32));
        verify(repo, times(2)).findPass("NOPE");
    }

    @Test
    void missingHrProfileStillLogsInWithoutName() {
        when(hr.findProfile("E001")).thenReturn(Optional.empty());
        assertEquals(new CurrentUser("E001", null, null, null), service.login("E001", "Secr3t!"));
    }

    @Test
    void hrLookupFailureDoesNotFailLogin() {
        when(hr.findProfile("E001")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));
        assertEquals(new CurrentUser("E001", null, null, null), service.login("E001", "Secr3t!"));
    }

    @Test
    void resignedEmployeeIsRejectedOnlyWhenBlockingIsOn() {
        when(hr.findProfile("E001")).thenReturn(Optional.of(new HrProfile("A", "PE", "Press", true)));
        UnauthorizedException e = assertThrows(UnauthorizedException.class, () -> service.login("E001", "Secr3t!"));
        assertEquals(AuthService.INVALID_MESSAGE, e.getMessage());
        assertEquals("A", new AuthService(repo, hr, false, false).login("E001", "Secr3t!").name());
    }

    @Test
    void hrIsNotQueriedWhenThePasswordIsWrong() {
        fail("E001", "bad");
        verify(hr, never()).findProfile(anyString());
    }

    @Test
    void resignedMeansDateSetAndNotAfterToday() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 2);
        assertFalse(HrRepository.resigned(null, today));
        assertTrue(HrRepository.resigned(java.sql.Date.valueOf("2026-10-02"), today));
        assertFalse(HrRepository.resigned(java.sql.Date.valueOf("2026-10-03"), today));
    }

    @Test
    void lastLoginIsUpdatedOnlyWhenEnabled() {
        AuthService enabled = new AuthService(repo, hr, true, true);
        enabled.login("E001", "Secr3t!");
        verify(repo).updateLastLogin("E001");
        fail("E001", "bad");
        verify(repo, times(1)).updateLastLogin(anyString());
    }

    @Test
    void fixedCharPasswordsAreRightTrimmedOnly() {
        assertEquals("  Ab", AuthRepository.rtrim("  Ab   "));
        assertEquals("", AuthRepository.rtrim("   "));
    }
}
