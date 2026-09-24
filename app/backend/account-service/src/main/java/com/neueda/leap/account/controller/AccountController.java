package com.neueda.leap.account.controller;

import org.springframework.web.bind.annotation.*;

/**
 * Account Service Controller
 * Handles account management endpoints
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @GetMapping("/{accountId}")
    public String getAccount(@PathVariable Long accountId) {
        return "Account Service - Account: " + accountId;
    }

    @GetMapping
    public String getAllAccounts() {
        return "Account Service - All Accounts";
    }
}
