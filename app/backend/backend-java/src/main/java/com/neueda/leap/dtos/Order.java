package com.neueda.leap.dtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.OrderSide;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Order(

    @Schema(description="Order ID (UUID)", example="550e8400-e29b-41d4-a716-446655440000")
    @NotNull(message="ID must not be null")
    @NotBlank(message="ID must not be blank")
    String id,

    @Schema(description="Account ID that placed the order")
    @NotNull(message="Account ID must not be null")
    @NotBlank(message="Account ID must not be blank")
    String accountId,

    @Schema(description="Trading symbol/instrument identifier")
    @NotNull(message="Instrument ID must not be null")
    @NotBlank(message="Instrument ID must not be blank")
    String instrumentId,

    @Schema(description="Order direction")
    @NotNull(message="Order side must not be null")
    OrderSide side,

    @Schema(description="Number of units", example="100")
    @NotNull(message="Quantity must not be null")
    int quantity,

    @Schema(description="Order price per unit", example="150.50")
    @NotNull(message="Price must not be null")
    BigDecimal price,

    @Schema(description="Current order status")
    @NotNull(message="Order status must not be null")
    OrderStatus status,

    @Schema(description="Idempotency key for duplicate prevention", example="IDEMP-0001")
    @NotNull(message="Idempotency key must not be null")
    @NotBlank(message="Idempotency key must not be blank")
    String idempotencyKey,

    @Schema(description="Order creation timestamp", example="2026-09-22T10:15:00Z")
    @NotNull(message="Creation time must not be null")
    LocalDateTime createdAt
){
    public Order(String id, String accountId, String instrumentId, 
        OrderSide side, int quantity,  BigDecimal price, 
        OrderStatus status, String idempotencyKey, LocalDateTime createdAt){
        this.id = id;
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }
    
}
