package com.neueda.trading.app.messaging;

import com.neueda.trading.app.enums.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message on the {@code executions} topic: the execution engine's report
 * that an order was filled. {@code price} is the fill price, which is at or
 * better than the order's {@code limitPrice}.
 */
public record ExecutionEvent(
        UUID executionId,
        UUID orderId,
        String accountId,
        String symbol,
        OrderSide side,
        int quantity,
        BigDecimal price,
        BigDecimal limitPrice,
        String venue,
        Instant executedOn
) {
}
