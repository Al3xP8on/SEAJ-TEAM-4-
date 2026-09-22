package com.neueda.leap.enums;

public enum OrderErrorCode {
    ACCOUNT_NOT_ACTIVE("Account is not in active state"),
    INSTRUMENT_NOT_TRADABLE("Instrument is not tradable"),
    INVALID_QUANTITY("Quantity must be at least the minimum"),
    INVALID_PRICE("Price must be positive"),
    EMPTY_IDEMPOTENCY_KEY("Idempotency key cannot be empty"),
    IDEMPOTENCY_KEY_TOO_LONG("Idempotency key exceeds 100 characters"),
    INVALID_STATE_TRANSITION("Invalid order state transition"),
    EXECUTION_FAILED("Order execution failed"),
    EXECUTION_ERROR("Order execution error"),
    CANCEL_INVALID_STATE("Order cannot be cancelled in current state"),
    INSUFFICIENT_FUNDS("Insufficient funds for order execution");

    private final String description;

    OrderErrorCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
