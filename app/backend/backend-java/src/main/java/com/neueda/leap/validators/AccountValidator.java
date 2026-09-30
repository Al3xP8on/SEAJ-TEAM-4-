package com.neueda.leap.validators;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AccountValidator {

    private AccountValidator() {}

    public static void validateAccountId(String accountId) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        if (accountId.trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID cannot be empty");
        }
    }

    public static void validateHolderName(String holderName) {
        Objects.requireNonNull(holderName, "Holder name cannot be null");
        if (holderName.trim().isEmpty()) {
            throw new IllegalArgumentException("Holder name cannot be empty");
        }
    }

    public static void validatePositiveAmount(BigDecimal amount, String fieldName) {
        Objects.requireNonNull(amount, fieldName + " cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    public static void validateCanClose(BigDecimal cashBalance) {
        if (cashBalance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("Account cannot be closed with non-zero balance");
        }
    }
}
