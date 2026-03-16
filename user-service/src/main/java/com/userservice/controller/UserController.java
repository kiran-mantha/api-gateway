package com.userservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import java.util.Map;

import com.userservice.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Map<String, Object> getUser(@PathVariable Long id) {
        return Map.of(
            "id", id,
            "name", "John Doe",
            "email", "john@example.com",
            "source", "user-service"
        );
    }

    @GetMapping
    public Map<String, Object> listUsers() {
        return Map.of("users", java.util.List.of("user-1", "user-2"), "source", "user-service");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        userService.deleteUser(id, authHeader);
        return ResponseEntity.noContent().build();   // 204
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader) {
        userService.logout(authHeader);
        return ResponseEntity.noContent().build();   // 204
    }
}