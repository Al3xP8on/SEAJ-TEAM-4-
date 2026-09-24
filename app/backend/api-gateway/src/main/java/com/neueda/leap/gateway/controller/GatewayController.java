package com.neueda.leap.gateway.controller;

import org.springframework.web.bind.annotation.*;

/**
 * API Gateway Controller
 * Central entry point for routing requests to appropriate services
 */
@RestController
@RequestMapping("/api/gateway")
public class GatewayController {

    @GetMapping("/health")
    public String health() {
        return "API Gateway is running";
    }

    @GetMapping("/info")
    public String info() {
        return "SEAJ Trading Platform - Unified Application";
    }
}
