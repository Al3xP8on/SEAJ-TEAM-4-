package com.neueda.leap.controllers;

import com.neueda.leap.models.Account;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.models.Positions;
import com.neueda.leap.dtos.AccountInput;
import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.dtos.LoginRequest;
import com.neueda.leap.dtos.LoginResponse;
import com.neueda.leap.exceptions.DuplicateAccountException;
import com.neueda.leap.services.AccountService;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.security.AuthorizationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/accounts")
@Tag(name = "Accounts", description = "Account management and authentication endpoints")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private SecurityUtilsService securityUtilsService;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${auth.service.url:http://auth-service:3000}")
    private String authServiceUrl;

    @PostMapping("/login")
    @Operation(summary = "Get authentication token", 
               description = "Authenticate with username and password to receive a JWT token for API access. Use this token in the Authorization header: 'Bearer <token>'")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // Call the NestJS auth service to get token
            String authUrl = authServiceUrl + "/auth/login";
            
            Map<String, String> loginPayload = new HashMap<>();
            loginPayload.put("username", request.getUsername());
            loginPayload.put("password", request.getPassword());
            
            ResponseEntity<?> response = restTemplate.postForEntity(authUrl, loginPayload, Object.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) response.getBody();
                String token = (String) body.get("access_token");
                Long expiresIn = body.get("expiresIn") != null ? 
                    Long.parseLong(body.get("expiresIn").toString()) : 1800L;
                
                LoginResponse loginResponse = new LoginResponse(
                    token,
                    request.getUsername(),
                    expiresIn
                );
                
                return ResponseEntity.ok(loginResponse);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication failed");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Authentication failed: " + e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "Create a new account", 
               description = "Register a new user account. After creating an account, use /v1/accounts/login to get an authentication token.")
    public ResponseEntity<?> createAccount(@Valid @RequestBody AccountInput input) {
        try {
            AccountResponse response = accountService.createAccount(input);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (DuplicateAccountException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping
    @Operation(summary = "List accounts", description = "Get all accounts (admin) or just your own account (regular user)")
    public ResponseEntity<?> getAllAccounts() {
        try {
            // If admin, return all accounts; otherwise return only user's account
            if (securityUtilsService.isAdmin()) {
                return ResponseEntity.ok(accountService.getAllAccounts());
            } else {
                Account userAccount = securityUtilsService.getAuthenticatedUserAccount();
                return ResponseEntity.ok(java.util.List.of(userAccount));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving accounts: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account details", description = "Retrieve details for a specific account. You can only access your own account unless you are an admin.")
    public ResponseEntity<Account> getAccount(@PathVariable Long id) {
        try {
            Account account = accountService.getAccount(id);
            // Validate user owns this account (unless admin)
            if (!securityUtilsService.isAdmin()) {
                securityUtilsService.validateAccountOwnership(account.getAccountId());
            }
            return ResponseEntity.ok(account);
        } catch (AuthorizationException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get account balance", description = "Retrieve the current balance for an account. You can only access your own account balance unless you are an admin.")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable Long id) {
        try {
            Account account = accountService.getAccount(id);
            // Validate user owns this account (unless admin)
            if (!securityUtilsService.isAdmin()) {
                securityUtilsService.validateAccountOwnership(account.getAccountId());
            }
            BigDecimal balance = accountService.getBalance(id);
            return ResponseEntity.ok(balance);
        } catch (AuthorizationException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    @GetMapping("/{id}/positions")
    @Operation(summary = "Get account positions", description = "Retrieve current positions held in an account. You can only access your own positions unless you are an admin.")
    public ResponseEntity<Positions> getPositions(@PathVariable Long id) {
        try {
            Account account = accountService.getAccount(id);
            // Validate user owns this account (unless admin)
            if (!securityUtilsService.isAdmin()) {
                securityUtilsService.validateAccountOwnership(account.getAccountId());
            }
            Positions positions = accountService.getPositions(id);
            return ResponseEntity.ok(positions);
        } catch (AuthorizationException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    @GetMapping("/{id}/orders")
    @Operation(summary = "Get account order history", description = "Retrieve the order history for an account. You can only access your own order history unless you are an admin.")
    public ResponseEntity<OrderHistory> getOrders(@PathVariable Long id) {
        try {
            Account account = accountService.getAccount(id);
            // Validate user owns this account (unless admin)
            if (!securityUtilsService.isAdmin()) {
                securityUtilsService.validateAccountOwnership(account.getAccountId());
            }
            OrderHistory orderHistory = accountService.getOrders(id);
            return ResponseEntity.ok(orderHistory);
        } catch (AuthorizationException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }
}
