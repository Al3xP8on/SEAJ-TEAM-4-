package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Message written to the {@code executions} topic: a full fill of one order. */
public record ExecutionEvent(
        UUID executionId,
        UUID orderId,
        String accountId,
        String symbol,
        Side side,
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
            Side side, int quantity, BigDecimal price, BigDecimal limitPrice, String venue, Instant executedOn) {
        this(executionId, orderId, accountId, symbol, side, quantity, price, limitPrice, venue,
                executedOn, null, null, null, null);
    }
}
