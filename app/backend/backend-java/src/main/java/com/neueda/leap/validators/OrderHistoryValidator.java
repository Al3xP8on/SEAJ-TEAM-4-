package com.neueda.leap.validators;

import java.util.Objects;

import com.neueda.leap.enums.OrderStatus;

public class OrderHistoryValidator {
    private static Long validateId(Long id) {
        Objects.requireNonNull(id, "History ID cannot be null");
        if (id <= 0) {
            throw new IllegalArgumentException("History ID must be positive");
        }
        return id;
    }

    private static String validateOrderId(String orderId) {
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        String trimmed = orderId.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Order ID cannot be empty");
        }
        // Basic UUID validation - should be 36 chars (with hyphens)
        if (trimmed.length() != 36 && !trimmed.matches("^[a-f0-9-]+$")) {
            throw new IllegalArgumentException("Order ID must be a valid UUID");
        }
        return trimmed;
    }

    private static OrderStatus validateStatus(OrderStatus status) {
        return Objects.requireNonNull(status, "Status cannot be null");
    }
}
