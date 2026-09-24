package com.neueda.leap.position.controller;

import org.springframework.web.bind.annotation.*;

/**
 * Positions Service Controller
 * Handles position management and P&L calculation endpoints
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {

    @GetMapping("/accounts/{accountId}")
    public String getPositions(@PathVariable Long accountId) {
        return "Positions Service - Account " + accountId + " Positions";
    }

    @GetMapping
    public String getAllPositions() {
        return "Positions Service - All Positions";
    }
}
