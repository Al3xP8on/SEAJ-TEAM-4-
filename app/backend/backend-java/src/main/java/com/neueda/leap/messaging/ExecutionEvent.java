package com.neueda.leap.messaging;

import com.neueda.leap.enums.OrderSide;

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
        Instant executedOn,
        Instant quoteTimestamp,
        UUID quoteEventId,
        BigDecimal bidPrice,
        BigDecimal askPrice
) {
    public ExecutionEvent(UUID executionId, UUID orderId, String accountId, String symbol,
            OrderSide side, int quantity, BigDecimal price, BigDecimal limitPrice, String venue, Instant executedOn) {
        this(executionId, orderId, accountId, symbol, side, quantity, price, limitPrice, venue,
                executedOn, null, null, null, null);
    }
}
