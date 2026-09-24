package com.neueda.leap.enums;

/**
 * OrderStatus enumeration representing the lifecycle states of an order.
 */
public enum OrderStatus {
    NEW("NEW", "Order created but not yet processed"),
    PENDING("PENDING", "Order awaiting execution"),
    EXECUTED("EXECUTED", "Order successfully executed"),
    FILLED("FILLED", "Order filled"),
    CANCELLED("CANCELLED", "Order has been cancelled"),
    REJECTED("REJECTED", "Order rejected due to validation failure");

    private final String code;
    private final String description;

    OrderStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    // Check if order can transition to PENDING state.
    public boolean canTransitionToPending() {
        return this == NEW;
    }

    // Check if order can transition to EXECUTED state.
    public boolean canTransitionToExecuted() {
        return this == PENDING || this == NEW;
    }

    // Check if order can be cancelled.
    public boolean canBeCancelled() {
        return this == NEW || this == PENDING;
    }

    // Check if order is in a terminal state.
    public boolean isTerminal() {
        return this == EXECUTED || this == CANCELLED || this == REJECTED;
    }

    // Check if order is filled (EXECUTED state).
    public boolean isFilled() {
        return this == EXECUTED;
    }
}
