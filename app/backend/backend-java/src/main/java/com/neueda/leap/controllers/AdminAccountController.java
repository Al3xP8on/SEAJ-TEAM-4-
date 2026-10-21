package com.neueda.leap.controllers;

import com.neueda.leap.dtos.AdminAccountResponse;
import com.neueda.leap.dtos.AdminAccountStatusRequest;
import com.neueda.leap.dtos.AdminAccountStatusHistoryResponse;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.services.AdminAccountService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {
    private final AdminAccountService accounts;
    private final SecurityUtilsService security;

    public AdminAccountController(AdminAccountService accounts, SecurityUtilsService security) {
        this.accounts = accounts;
        this.security = security;
    }

    @GetMapping
    public List<AdminAccountResponse> list() {
        return accounts.list();
    }

    @GetMapping("/{id}/history")
    public List<AdminAccountStatusHistoryResponse> history(@PathVariable Long id) {
        return accounts.history(id);
    }

    @PutMapping("/{id}/status")
    public AdminAccountResponse changeStatus(@PathVariable Long id, @Valid @RequestBody AdminAccountStatusRequest request) {
        return accounts.changeStatus(id, request, security.getAuthenticatedUserAccount().getId());
    }
}
