package com.cloudrec.controller;

import com.cloudrec.service.AuthService;
import com.cloudrec.service.JwtService;
import com.cloudrec.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;
    private final JwtService jwtService;
    /** httpOnly so page scripts (and any XSS) can't read the token. */
    public static final String AUTH_COOKIE = "auth_token";

    private final LoginAttemptService attempts;
    private final boolean secureCookie;

    public AuthController(AuthService service, JwtService jwtService, LoginAttemptService attempts,
                          @Value("${security.cookie.secure:true}") boolean secureCookie) {
        this.service = service;
        this.jwtService = jwtService;
        this.attempts = attempts;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body, HttpServletRequest request) {

        if (!attempts.allowRequestFrom(request.getRemoteAddr())) {
            return tooManyRequests("Too many requests, please wait a minute", 60);
        }

        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");

        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return ResponseEntity.badRequest().body("Invalid email address");
        }
        if (password.length() < 8) {
            return ResponseEntity.badRequest().body("Password must be at least 8 characters");
        }
        if (password.length() > 72) {
            // BCrypt ignores bytes past 72, so longer passwords would silently match truncated ones
            return ResponseEntity.badRequest().body("Password must be at most 72 characters");
        }

        String response = service.register(email, password);

        if (response.equals("User already exists")) {
            return ResponseEntity.status(409).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {

        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");

        if (!attempts.allowRequestFrom(request.getRemoteAddr())) {
            return tooManyRequests("Too many requests, please wait a minute", 60);
        }
        if (attempts.isLocked(email)) {
            return tooManyRequests("Too many failed attempts. Try again in " + attempts.lockMinutes() + " minutes",
                    attempts.lockMinutes() * 60);
        }

        try {
            service.login(email, password);
            attempts.recordSuccess(email);
            String token = jwtService.generateToken(email);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, authCookie(token, Duration.ofMillis(jwtService.expirationMs())).toString())
                    .body(Map.of("email", email));

        } catch (Exception e) {
            attempts.recordFailure(email);
            return ResponseEntity.status(401).body("Invalid email or password");
        }
    }

    /** Who the auth cookie belongs to; the SPA calls this on load since it can't read the cookie. */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).body(Map.of("error", "Not signed in"));
        }
        return ResponseEntity.ok(Map.of("email", auth.getName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseCookie authCookie(String value, Duration maxAge) {
        return ResponseCookie.from(AUTH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private static ResponseEntity<String> tooManyRequests(String message, long retryAfterSeconds) {
        return ResponseEntity.status(429).header("Retry-After", String.valueOf(retryAfterSeconds)).body(message);
    }
}
