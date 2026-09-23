package com.neueda.leap.dtos;

import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.OrderSide;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderResponse {
    private String orderId;
    private String accountId;
    private String accountHolderName;
    private String ticker;
    private String instrumentName;
    private long quantity;
    private BigDecimal price;
    private BigDecimal totalValue;
    private OrderSide side;
    private OrderStatus status;
    private String idempotencyKey;
    private LocalDateTime createdAt;

    public OrderResponse(String orderId, String accountId, String accountHolderName,
                        String ticker, String instrumentName, long quantity, BigDecimal price,
                        BigDecimal totalValue, OrderSide side, OrderStatus status,
                        String idempotencyKey, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.accountHolderName = accountHolderName;
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.quantity = quantity;
        this.price = price;
        this.totalValue = totalValue;
        this.side = side;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public String getTicker() {
        return ticker;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
