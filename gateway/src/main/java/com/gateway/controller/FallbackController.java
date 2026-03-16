package com.gateway.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/user-service")
    public ResponseEntity<Map<String, Object>> userServiceFallback(
            ServerWebExchange exchange) {

        log.warn("Circuit breaker OPEN — user-service unavailable, " +
                 "returning fallback for path={}",
                 exchange.getRequest().getURI().getPath());

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "status",    503,
                "error",     "Service Unavailable",
                "message",   "User service is temporarily unavailable. Please try again shortly.",
                "service",   "user-service",
                "timestamp", Instant.now().toString()
            ));
    }

    @GetMapping("/order-service")
    public ResponseEntity<Map<String, Object>> orderServiceFallback(
            ServerWebExchange exchange) {

        log.warn("Circuit breaker OPEN — order-service unavailable, " +
                 "returning fallback for path={}",
                 exchange.getRequest().getURI().getPath());

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "status",    503,
                "error",     "Service Unavailable",
                "message",   "Order service is temporarily unavailable. Please try again shortly.",
                "service",   "order-service",
                "timestamp", Instant.now().toString()
            ));
    }
}