package com.orderservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/{id}")
    public Map<String, Object> getOrder(@PathVariable Long id) {
        return Map.of(
            "id", id,
            "item", "Laptop",
            "quantity", 1,
            "source", "order-service"
        );
    }

    @GetMapping
    public Map<String, Object> listOrders() {
        return Map.of("orders", java.util.List.of("order-1", "order-2"), "source", "order-service");
    }
}