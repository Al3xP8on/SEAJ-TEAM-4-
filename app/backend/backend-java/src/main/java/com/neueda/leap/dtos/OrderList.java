package com.neueda.leap.dtos;

import java.util.List;
import java.util.Optional;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import io.swagger.v3.oas.annotations.media.Schema;
import com.neueda.leap.models.Order;

public record OrderList(
    @Schema(description="Account ID")
    @NotNull(message="Account ID must be provided")
    String accountId,

    @Schema(description="List of orders")
    @NotNull(message="Orders must be provided")
    List<Order> orders,

    @Schema(description="Total number of orders")
    @NotNull(message="Total count must be provided")
    @PositiveOrZero(message="Total count must be zero or positive")
    int totalCount,

    @Schema(description="Number of NEW orders")
    @PositiveOrZero(message="New count must be zero or positive")
    Optional<Integer> newCount,

    @Schema(description="Number of EXECUTED orders")
    @PositiveOrZero(message="Executed count must be zero or positive")
    Optional<Integer> executedCount,

    @Schema(description="Number of REJECTED orders")
    @PositiveOrZero(message="Rejected count must be zero or positive")
    Optional<Integer> rejectedCount,

    @Schema(description="Number of CANCELLED orders")
    @PositiveOrZero(message="Cancelled count must be zero or positive")
    Optional<Integer> cancelledCount
) {

    public OrderList(String accountId, List<Order> orders, int totalCount, Optional<Integer> newCount, Optional<Integer> executedCount, Optional<Integer> rejectedCount, Optional<Integer> cancelledCount){
        this.accountId = accountId;
        this.orders = orders;
        this.totalCount = totalCount;
        this.newCount = newCount;
        this.executedCount = executedCount;
        this.rejectedCount = rejectedCount;
        this.cancelledCount = cancelledCount;
    }
}
