package com.neueda.leap.controllers;

import com.neueda.leap.models.Account;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.models.Positions;
import com.neueda.leap.services.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {
    
    @Autowired
    private AccountService accountService;

    @GetMapping
    public ResponseEntity<?> getAllAccounts() {
        try {
            return ResponseEntity.ok(accountService.getAllAccounts());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving accounts: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable Long id) {
        try {
            Account account = accountService.getAccount(id);
            return ResponseEntity.ok(account);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable Long id) {
        try {
            BigDecimal balance = accountService.getBalance(id);
            return ResponseEntity.ok(balance);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    @GetMapping("/{id}/positions")
    public ResponseEntity<Positions> getPositions(@PathVariable Long id) {
        try {
            Positions positions = accountService.getPositions(id);
            return ResponseEntity.ok(positions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    @GetMapping("/{id}/orders")
    public ResponseEntity<OrderHistory> getOrders(@PathVariable Long id) {
        try {
            OrderHistory orderHistory = accountService.getOrders(id);
            return ResponseEntity.ok(orderHistory);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }
}
