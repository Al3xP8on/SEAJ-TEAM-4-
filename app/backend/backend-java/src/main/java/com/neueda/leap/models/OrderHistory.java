package com.neueda.leap.models;

import com.neueda.leap.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.Objects;

public class OrderHistory {
    
    private final Long id; 
    private final String orderId;  
    private final OrderStatus status;
    private final LocalDateTime changedOn;

    // No-arg constructor for ORM/serialization frameworks.
    public OrderHistory() {
        this.id = null;
        this.orderId = null;
        this.status = null;
        this.changedOn = null;
    }

    // Constructor for creating new order history records.
    // Used when recording a status change.
    public OrderHistory(String orderId, OrderStatus status) {
        this.id = null;
        this.orderId = validateOrderId(orderId);
        this.status = validateStatus(status);
        this.changedOn = LocalDateTime.now();
    }

    // Constructor for loading existing history records from database
    public OrderHistory(Long id, String orderId, OrderStatus status, LocalDateTime changedOn) {
        this.id = validateId(id);
        this.orderId = validateOrderId(orderId);
        this.status = validateStatus(status);
        this.changedOn = Objects.requireNonNull(changedOn, "Changed timestamp cannot be null");
    }

    //  validation methods 
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

    //  object contract methods
    //History records are equal if they have the same ID using the database identity.
    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof OrderHistory)) return false;
        
        OrderHistory history = (OrderHistory) object;
        return Objects.equals(this.id, history.id);
    }

    // Hash code based on history ID
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format(
            "OrderHistory{id=%s, orderId=%s, status=%s, changedOn=%s}",
            id,
            orderId,
            status,
            changedOn
        );
    }

    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getChangedOn() {
        return changedOn;
    }
}
