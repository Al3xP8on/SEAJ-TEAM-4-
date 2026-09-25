package com.neueda.leap.models;

import com.neueda.leap.enums.OrderStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "order_history")
public class OrderHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; 
    
    @Column(name = "order_id", nullable = false)
    private String orderId;  
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    
    @Column(name = "changed_on", nullable = false)
    private LocalDateTime changedOn;

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
        this.orderId = Objects.requireNonNull(orderId, "Order ID cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.changedOn = LocalDateTime.now();
    }

    // Constructor for loading existing history records from database
    public OrderHistory(Long id, String orderId, OrderStatus status, LocalDateTime changedOn) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.orderId = Objects.requireNonNull(orderId, "Order ID cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.changedOn = Objects.requireNonNull(changedOn, "Changed on timestamp cannot be null");
    }

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
