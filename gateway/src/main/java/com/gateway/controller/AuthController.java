package com.gateway.controller;

import com.gateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody Map<String, String> credentials) {

        // In production: validate credentials against your user DB
        // Here we just issue a token for any request (dev only)
        String userId = credentials.getOrDefault("userId", "user-123");
        String role   = credentials.getOrDefault("role",   "USER");

        String token = jwtUtil.generateTestToken(
            userId,
            role,
            3_600_000L   // 1 hour expiry
        );

        return ResponseEntity.ok(Map.of(
            "token", token,
            "type",  "Bearer",
            "userId", userId,
            "role",   role
        ));
    }
}