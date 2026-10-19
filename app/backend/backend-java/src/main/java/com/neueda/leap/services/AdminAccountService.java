package com.neueda.leap.services;

import com.neueda.leap.dtos.AdminAccountResponse;
import com.neueda.leap.dtos.AdminAccountStatusHistoryResponse;
import com.neueda.leap.dtos.AdminAccountStatusRequest;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.Account;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionsRepository;
import com.neueda.leap.repositories.AccountStatusHistoryRepository;
import com.neueda.leap.models.AccountStatusHistory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class AdminAccountService {
    private final AccountRepository accounts;
    private final PositionsRepository positions;
    private final OrderRepository orders;
    private final AccountStatusHistoryRepository statusHistory;

    public AdminAccountService(AccountRepository accounts, PositionsRepository positions, OrderRepository orders,
                               AccountStatusHistoryRepository statusHistory) {
        this.accounts = accounts;
        this.positions = positions;
        this.orders = orders;
        this.statusHistory = statusHistory;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> list() {
        return accounts.findAll().stream().map(AdminAccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAccountStatusHistoryResponse> history(Long id) {
        if (!accounts.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        return statusHistory.findByAccount_IdOrderByChangedOnDesc(id).stream()
                .map(AdminAccountStatusHistoryResponse::from).toList();
    }

    /** Shares the account lock used by deposits, order submission and settlement. */
    @Transactional
    public AdminAccountResponse changeStatus(Long id, AdminAccountStatusRequest request, Long administratorId) {
        AccountStatus status = request.status();
        if (request.requiresReason() && request.normalizedReason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required for this status change.");
        }
        if (id.equals(administratorId)) {
            throw conflict("You cannot change the status of your own admin account.");
        }
        Account account = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getStatus() == status) return AdminAccountResponse.from(account);
        AccountStatus previous = account.getStatus();
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw conflict("Closed accounts cannot be reopened.");
        }
        if (status == AccountStatus.CLOSED) {
            if (account.getCashBalance().signum() != 0) {
                throw conflict("Withdraw the remaining cash before closing this account.");
            }
            if (positions.findByAccountId(id).stream().anyMatch(position -> position.getQuantity() > 0)) {
                throw conflict("Sell or close all holdings before closing this account.");
            }
            if (orders.existsByAccount_IdAndStatusIn(id, List.of(OrderStatus.NEW, OrderStatus.PENDING))) {
                throw conflict("Cancel or complete pending orders before closing this account.");
            }
        }
        account.setStatus(status);
        Account saved = accounts.save(account);
        Account administrator = accounts.findById(administratorId).orElseThrow();
        statusHistory.save(new AccountStatusHistory(saved, administrator, previous, status,
            request.normalizedReason().isBlank() ? "No reason provided" : request.normalizedReason()));
        return AdminAccountResponse.from(saved);
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
