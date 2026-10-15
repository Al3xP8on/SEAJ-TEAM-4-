// Exposes account operations and forwards signup, login and email verification to the auth service.
// Preserves account access checks and returns safe errors without bypassing the email challenge flow.
package com.neueda.leap.controllers;

import com.neueda.leap.dtos.OrderHistoryEntry;
import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.models.Positions;
import java.util.List;
import com.neueda.leap.dtos.AccountInput;
import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.dtos.CashTransactionRequest;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.dtos.LoginRequest;
import com.neueda.leap.services.AccountService;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.security.AuthorizationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(value = "/v1/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
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

    @ApiResponse(responseCode = "202", description = "Email verification challenge; no tokens issued")
    @PostMapping("/login")
    @Operation(summary = "Start username/password login", description = "Returns an email verification challenge. Verify the code before receiving tokens.")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Map<String, String> payload = new HashMap<>();
        payload.put("username", request.getUsername());
        payload.put("password", request.getPassword());
        return authPost("/auth/login", payload, 202);
    }

    @ApiResponse(responseCode = "200", description = "Access and refresh tokens after email verification")
    @PostMapping("/login/verify")
    @Operation(summary = "Complete email second factor and receive tokens")
    public ResponseEntity<?> verifyLogin(@Valid @RequestBody com.neueda.leap.dtos.EmailVerificationRequest request) {
        return authPost("/auth/login/verify", Map.of("challenge_id", request.challenge_id(), "code", request.code()), 200);
    }

    @ApiResponse(responseCode = "202", description = "Pending registration email verification")
    @PostMapping
    @Operation(summary = "Start account registration", description = "Requires unique username and email; returns an email verification challenge.")
    public ResponseEntity<?> createAccount(@Valid @RequestBody AccountInput input) {
        Map<String, String> payload = new HashMap<>();
        payload.put("username", input.username());
        payload.put("email", input.email());
        payload.put("password", input.password());
        return authPost("/auth/register", payload, 202);
    }

    @ApiResponse(responseCode = "201", description = "Verified account with generated ACC account ID")
    @PostMapping("/register/verify")
    @Operation(summary = "Verify registration email and create account")
    public ResponseEntity<?> verifyRegistration(@Valid @RequestBody com.neueda.leap.dtos.EmailVerificationRequest request) {
        return authPost("/auth/register/verify", Map.of("challenge_id", request.challenge_id(), "code", request.code()), 201);
    }

    private ResponseEntity<?> authPost(String path, Map<String, String> payload, int expectedStatus) {
        try {
            ResponseEntity<?> response = restTemplate.postForEntity(authServiceUrl + path, payload, Object.class);
            if (response.getStatusCode().value() != expectedStatus || !(response.getBody() instanceof Map<?, ?> body)) {
                return authError(503);
            }
            if (expectedStatus == 202 && (!(body.get("challenge_id") instanceof String) || body.containsKey("access_token"))) {
                return authError(503);
            }
            return ResponseEntity.status(expectedStatus).body(body);
        } catch (org.springframework.web.client.HttpStatusCodeException exception) {
            int status = exception.getStatusCode().value();
            return authError(java.util.Set.of(400, 401, 409, 429, 503).contains(status) ? status : 503);
        } catch (Exception exception) {
            return authError(503);
        }
    }

    private ResponseEntity<?> authError(int status) {
        String message = switch (status) {
            case 400 -> "Invalid request";
            case 401 -> "Invalid credentials or verification code";
            case 409 -> "Email or username already exists";
            case 429 -> "Please wait before requesting another verification code";
            default -> "Authentication service is unavailable";
        };
        return ResponseEntity.status(status).body(Map.of("statusCode", status, "message", message, "error", HttpStatus.valueOf(status).getReasonPhrase()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Use the email-verified account deletion flow in My Account"));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
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
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String id) {
        try {
            Account account = accountService.getAccountByIdentifier(id);
            // Validate user owns this account (unless admin)
            if (!securityUtilsService.isAdmin()) {
                securityUtilsService.validateAccountOwnership(account.getAccountId());
            }
            return ResponseEntity.ok(AccountResponse.from(account));
        } catch (AuthorizationException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get account balance", description = "Retrieve the current balance for an account. You can only access your own account balance unless you are an admin.")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String id) {
        try {
            Account account = accountService.getAccountByIdentifier(id);
            // Validate user owns this account (unless admin)
            if (!securityUtilsService.isAdmin()) {
                securityUtilsService.validateAccountOwnership(account.getAccountId());
            }
            BigDecimal balance = accountService.getBalance(account.getId());
            return ResponseEntity.ok(balance);
        } catch (AuthorizationException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    @GetMapping("/{id}/positions")
    @Operation(operationId = "getAccountPositions", summary = "Get account positions", description = "Retrieve current positions held in an account. You can only access your own positions unless you are an admin.")
    public ResponseEntity<List<PositionResponse>> getPositions(@PathVariable String id) {
        try {
            Account account = accountService.getAccountByIdentifier(id);
            // Validate user owns this account (unless admin)
            if (!securityUtilsService.isAdmin()) {
                securityUtilsService.validateAccountOwnership(account.getAccountId());
            }
            List<Positions> positions = accountService.getPositions(account.getId());
            return ResponseEntity.ok(positions.stream().map(PositionResponse::from).toList());
        } catch (AuthorizationException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    @GetMapping("/{id}/orders")
    @Operation(summary = "Get account order history", description = "Retrieve the order history for an account. You can only access your own order history unless you are an admin.")
    public ResponseEntity<OrderHistoryEntry> getOrders(@PathVariable String id) {
        try {
            Account account = accountService.getAccountByIdentifier(id);
            // Validate user owns this account (unless admin)
            if (!securityUtilsService.isAdmin()) {
                securityUtilsService.validateAccountOwnership(account.getAccountId());
            }
            OrderHistory orderHistory = accountService.getOrders(account.getId());
            return ResponseEntity.ok(OrderHistoryEntry.from(orderHistory));
        } catch (AuthorizationException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    @PostMapping("/{id}/deposit")
    @Operation(summary = "Deposit cash", description = "Add cash to your account. Returns the account with its updated balance.")
    public ResponseEntity<AccountResponse> deposit(@PathVariable String id, @Valid @RequestBody CashTransactionRequest request)
            throws AccountNotFoundException, AccountNotActiveException {
        Account account = accountService.getAccountByIdentifier(id);
        securityUtilsService.validateAccountOwnership(account.getAccountId());
        return ResponseEntity.ok(accountService.deposit(account.getId(), request.amount()));
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw cash", description = "Take cash out of your account. Fails with 400 if the balance is too low.")
    public ResponseEntity<AccountResponse> withdraw(@PathVariable String id, @Valid @RequestBody CashTransactionRequest request)
            throws AccountNotFoundException, AccountNotActiveException, InsufficientFundsException {
        Account account = accountService.getAccountByIdentifier(id);
        securityUtilsService.validateAccountOwnership(account.getAccountId());
        return ResponseEntity.ok(accountService.withdraw(account.getId(), request.amount()));
    }
}
