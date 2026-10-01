package com.neueda.leap.messaging;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a trade event that flows through the trade-events Kafka topic.
 * Events are keyed by accountId to ensure ordering per account.
 */
public class TradeEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private UUID tradeId;
    private String accountId;
    private String symbol;
    private String side;                    // BUY or SELL
    private BigDecimal price;
    private long quantity;
    private String status;                  // INITIATED, ACCEPTED, EXECUTED, FILLED, REJECTED, CANCELLED
    private String timeInForce;             // GTC, GTD, IOC, FOK
    private LocalDateTime timestamp;
    private String reason;                  // For rejections/cancellations
    
    // Constructors
    public TradeEvent() {
    }
    
    public TradeEvent(UUID tradeId, String accountId, String symbol, String side, 
                      BigDecimal price, long quantity, String status, LocalDateTime timestamp) {
        this.tradeId = tradeId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.status = status;
        this.timestamp = timestamp;
        this.timeInForce = "GTC";  // Default
    }
    
    public TradeEvent(UUID tradeId, String accountId, String symbol, String side, 
                      BigDecimal price, long quantity, String status, String timeInForce, LocalDateTime timestamp) {
        this.tradeId = tradeId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.status = status;
        this.timeInForce = timeInForce;
        this.timestamp = timestamp;
    }
    
    // Getters and Setters
    public UUID getTradeId() {
        return tradeId;
    }
    
    public void setTradeId(UUID tradeId) {
        this.tradeId = tradeId;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getSide() {
        return side;
    }
    
    public void setSide(String side) {
        this.side = side;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public long getQuantity() {
        return quantity;
    }
    
    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getTimeInForce() {
        return timeInForce;
    }
    
    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }
    
    @Override
    public String toString() {
        return "TradeEvent{" +
                "tradeId=" + tradeId +
                ", accountId='" + accountId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side='" + side + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", status='" + status + '\'' +
                ", timeInForce='" + timeInForce + '\'' +
                ", timestamp=" + timestamp +
                ", reason='" + reason + '\'' +
                '}';
    }
}
