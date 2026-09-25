package com.neueda.leap.dtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.OrderSide;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse (
    @Schema(description = "Order ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotNull(message = "Order ID must not be null")
    @NotBlank(message = "Order ID must not be blank")
    String orderId,

    @Schema(description = "Account ID that placed the order", example = "ACC-001")
    @NotNull(message = "Account ID must not be null")
    @NotBlank(message = "Account ID must not be blank")
    String accountId,

    @Schema(description = "Name of the account holder", example = "Alice Johnson")
    @NotNull(message = "Account holder name must not be null")
    @NotBlank(message = "Account holder name must not be blank")
    String accountHolderName,

    @Schema(description = "Trading symbol/instrument identifier", example = "AAPL")
    @NotNull(message = "Ticker must not be null")
    @NotBlank(message = "Ticker must not be blank")
    String ticker,

    @Schema(description = "Name of the trading instrument", example = "Apple Inc.")
    @NotNull(message = "Instrument name must not be null")
    @NotBlank(message = "Instrument name must not be blank")
    String instrumentName,

    @Schema(description = "Number of units", example = "100")
    @NotNull(message = "Quantity must not be null")
    @Positive(message = "Quantity must be positive")
    long quantity,

    @Schema(description = "Order price per unit", example = "150.50")
    @NotNull(message = "Price must not be null")
    @Positive(message = "Price must be positive")
    BigDecimal price,

    @Schema(description = "Total value of the order (quantity * price)", example = "15050.00")
    @NotNull(message = "Total value must not be null")
    @Positive(message = "Total value must be positive")
    BigDecimal totalValue,

    @Schema(description = "Order direction")
    @NotNull(message = "Order side must not be null")
    OrderSide side,

    @Schema(description = "Current order status")
    @NotNull(message = "Order status must not be null")
    OrderStatus status,

    @Schema(description = "Idempotency key for duplicate prevention", example = "IDEMP-20260922-001")
    @NotNull(message = "Idempotency key must not be null")
    @NotBlank(message = "Idempotency key must not be blank")
    String idempotencyKey,

    @Schema(description = "Order creation timestamp", example = "2026-09-22T10:15:00Z")
    @NotNull(message = "Creation time must not be null")
    LocalDateTime createdAt

){

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

}
