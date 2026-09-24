package com.neueda.leap.order.controller;

import org.springframework.web.bind.annotation.*;

/**
 * Order Service Controller
 * Handles order management and execution endpoints
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping
    public String getAllOrders() {
        return "Order Service - All Orders";
    }

    @PostMapping
    public String createOrder() {
        return "Order Service - Order Created";
    }

    @GetMapping("/{orderId}")
    public String getOrder(@PathVariable Long orderId) {
        return "Order Service - Order: " + orderId;
    }
}
