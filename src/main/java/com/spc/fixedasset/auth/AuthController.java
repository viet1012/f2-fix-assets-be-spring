package com.spc.fixedasset.auth;

import com.spc.fixedasset.dto.LoginRequest;
import com.spc.fixedasset.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Session login (no JWT). Session id is replaced on every successful login (session fixation). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping(value = "/login", consumes = "application/json")
    public CurrentUser login(@RequestBody(required = false) LoginRequest body, HttpServletRequest request) {
        String account = body == null || body.account() == null ? "" : body.account().trim();
        if (account.isEmpty() || body.password() == null || body.password().isEmpty()) {
            throw new BadRequestException("account và password là bắt buộc.");
        }
        CurrentUser user = service.login(account, body.password());
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthSession.ACCOUNT, user.account());
        session.setAttribute(AuthSession.USER, user);
        session.setMaxInactiveInterval((int) AuthSession.TIMEOUT.toSeconds());
        return user;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUser me(HttpServletRequest request) {
        return AuthSession.requireUser(request);
    }
}
