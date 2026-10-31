package com.cloudrec.controller;

import com.cloudrec.service.AuthService;
import com.cloudrec.service.JwtService;
import com.cloudrec.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;
    private final JwtService jwtService;
    private final LoginAttemptService attempts;

    public AuthController(AuthService service, JwtService jwtService, LoginAttemptService attempts) {
        this.service = service;
        this.jwtService = jwtService;
        this.attempts = attempts;
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
            return ResponseEntity.ok(Map.of("token", token, "email", email));

        } catch (Exception e) {
            attempts.recordFailure(email);
            return ResponseEntity.status(401).body("Invalid email or password");
        }
    }

    private static ResponseEntity<String> tooManyRequests(String message, long retryAfterSeconds) {
        return ResponseEntity.status(429).header("Retry-After", String.valueOf(retryAfterSeconds)).body(message);
    }
}
