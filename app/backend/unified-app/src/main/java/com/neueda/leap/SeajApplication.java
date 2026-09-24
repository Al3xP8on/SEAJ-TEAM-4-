package com.neueda.leap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main entry point for SEAJ Trading Platform
 * Unified application that bundles all microservices:
 * - Account Service
 * - Order Service
 * - Positions Service
 * - API Gateway
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.neueda.leap.shared",
    "com.neueda.leap.gateway",
    "com.neueda.leap.account",
    "com.neueda.leap.order",
    "com.neueda.leap.position"
})
public class SeajApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeajApplication.class, args);
    }
}
