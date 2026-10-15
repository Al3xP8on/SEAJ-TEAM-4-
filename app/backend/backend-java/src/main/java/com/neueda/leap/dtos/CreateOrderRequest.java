package com.neueda.leap.dtos;

import java.math.BigDecimal;
import com.neueda.leap.enums.OrderSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateOrderRequest(
    @Schema(description="Account ID placing the order")
    @NotBlank(message="Account ID must not be blank")
    String accountId,

    @Schema(description="Trading symbol/instrument identifier")
    @NotBlank(message="Instrument ID must not be blank")
    String instrumentId,

    @Schema(description="Number of units to order (must be positive)", example="100")
    @Positive(message="Quantity must be positive")
    int quantity,

    @Schema(description="Price per unit (must be positive)", example="150.50")
    @Positive(message="Price must be positive")
    BigDecimal price,

    @Schema(description="Order direction")
    @NotNull(message="Order side must not be null")
    OrderSide side,
    
    @Schema(description="Unique key for idempotency")
    @NotBlank(message="Idempotency key must not be blank")
    String idempotencyKey
) {}
