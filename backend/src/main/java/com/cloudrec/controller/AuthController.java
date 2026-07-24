package com.cloudrec.controller;

import com.cloudrec.service.AuthService;
import com.cloudrec.service.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;
    private final JwtService jwtService;

    public AuthController(AuthService service, JwtService jwtService) {
        this.service = service;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {

        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");

        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return ResponseEntity.badRequest().body("Invalid email address");
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body("Password must be at least 6 characters");
        }

        String response = service.register(email, password);

        if (response.equals("User already exists")) {
            return ResponseEntity.status(409).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {

        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");

        try {
            service.login(email, password);
            String token = jwtService.generateToken(email);
            return ResponseEntity.ok(Map.of("token", token, "email", email));

        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }
    }
}
