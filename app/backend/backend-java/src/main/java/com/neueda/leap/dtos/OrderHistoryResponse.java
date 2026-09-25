package com.neueda.leap.dtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Optional;

public record OrderHistoryResponse(
    @Schema(description="Order ID", example="550e8400-e29b-41d4-a716-446655440000")
    @NotNull(message="Order ID must be provided")
    @NotBlank(message="Order ID must not be blank")
    String orderId,

    @Schema(description="List (chronological) of status changes")
    @NotNull(message="Order history must be provided")
    List<OrderHistoryEntry> history,

    @Schema(description="Total number of status transitions")
    @PositiveOrZero(message="Total status changes must be zero or positive")
    Optional<Integer> totalStatusChanges
){
    public OrderHistoryResponse(String orderId, List<OrderHistoryEntry> history, Optional<Integer> totalStatusChanges){
        this.orderId = orderId;
        this.history = history;
        this.totalStatusChanges = totalStatusChanges;
    }
}