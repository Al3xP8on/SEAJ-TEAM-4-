package com.neueda.leap.dtos;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.models.Account;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Account management data, without credentials, sessions or payment details. */
public record AdminAccountResponse(Long id, String accountId, String username, String holderName,
        String email, String roles, BigDecimal cashBalance, AccountStatus status, LocalDateTime lastUpdated) {
    public static AdminAccountResponse from(Account account) {
        return new AdminAccountResponse(account.getId(), account.getAccountId(), account.getUsername(),
                account.getName(), account.getEmail(), account.getRoles(), account.getCashBalance(),
                account.getStatus(), account.getLastUpdated());
    }
}
