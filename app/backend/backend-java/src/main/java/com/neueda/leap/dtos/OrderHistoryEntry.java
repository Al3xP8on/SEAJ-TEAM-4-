package com.neueda.leap.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Optional;
import com.neueda.leap.enums.OrderStatus;

public record OrderHistoryEntry (
    @Schema(description="Order ID", example="550e8400-e29b-41d4-a716-446655440000")
    @NotNull(message="Order ID must be provided")
    @NotBlank(message="Order ID must not be blank")
    String orderId,

    @Schema(description="Status at this point in history")
    @NotNull(message="Order status must be provided")
    OrderStatus status,

    @Schema(description="When this status change occurred", example="2026-09-22T10:30:00Z")
    @NotNull(message="Timestamp must be provided")
    LocalDateTime timestamp,

    @Schema(description="Reason for status change")
    Optional<String> reason
) {
    public OrderHistoryEntry(String orderId, OrderStatus status, LocalDateTime timestamp, Optional<String> reason){
        this.orderId = orderId;
        this.status = status;
        this.timestamp = timestamp;
        this.reason = reason;
    }
}
