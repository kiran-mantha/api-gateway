package com.userservice.controller;

import com.userservice.dto.LoginRequest;
import com.userservice.dto.LoginResponse;
import com.userservice.dto.RegisterRequest;
import com.userservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(authService.register(request));
    }

    // Keep this for debugging — remove in production
    @GetMapping("/headers")
    public ResponseEntity<Map<String, String>> echoHeaders(
            @RequestHeader(value = "X-User-Id",    required = false) String userId,
            @RequestHeader(value = "X-User-Role",  required = false) String role,
            @RequestHeader(value = "X-User-Email", required = false) String email) {
        return ResponseEntity.ok(Map.of(
            "X-User-Id",    userId    != null ? userId    : "not set",
            "X-User-Role",  role      != null ? role      : "not set",
            "X-User-Email", email     != null ? email     : "not set"
        ));
    }
}