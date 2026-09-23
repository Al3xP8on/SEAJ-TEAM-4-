package com.neueda.leap.dtos;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import io.swagger.v3.oas.annotations.media.Schema;

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
    int newCount,

    @Schema(description="Number of EXECUTED orders")
    @PositiveOrZero(message="Executed count must be zero or positive")
    int executedCount,

    @Schema(description="Number of REJECTED orders")
    @PositiveOrZero(message="Rejected count must be zero or positive")
    int rejectedCount,

    @Schema(description="Number of CANCELLED orders")
    @PositiveOrZero(message="Cancelled count must be zero or positive")
    int cancelledCount
) {

    public OrderList(String accountId, List<Order> orders, int totalCount, int newCount, int executedCount, int rejectedCount, int cancelledCount){
        this.accountId = accountId;
        this.orders = orders;
        this.totalCount = totalCount;
        this.newCount = newCount;
        this.executedCount = executedCount;
        this.rejectedCount = rejectedCount;
        this.cancelledCount = cancelledCount;
    }
}
