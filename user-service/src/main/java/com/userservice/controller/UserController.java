package com.userservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
class UserController {

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
}