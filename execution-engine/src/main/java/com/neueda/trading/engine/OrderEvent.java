package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message read from the {@code orders} topic. Mirrors the trade API's
 * {@code OrderEvent}; the two services share the JSON contract documented in
 * {@code docs/event-flow.md}, not a code dependency.
 */
public record OrderEvent(
        UUID orderId,
        String accountId,
        String symbol,
        Side side,
        int quantity,
        BigDecimal price,
        Instant createdOn,
        String orderType,
        String action
) {
    public OrderEvent {
        orderType = orderType == null ? "LIMIT" : orderType;
        action = action == null ? "SUBMIT" : action;
        if (orderId == null || accountId == null || accountId.isBlank() || symbol == null || symbol.isBlank()
                || side == null || quantity <= 0 || price == null || price.signum() <= 0
                || !java.util.Set.of("LIMIT", "MARKET").contains(orderType)
                || !java.util.Set.of("SUBMIT", "CANCEL", "REJECT").contains(action))
            throw new IllegalArgumentException("Invalid order command");
    }
    public OrderEvent(UUID orderId, String accountId, String symbol, Side side,
            int quantity, BigDecimal price, Instant createdOn) {
        this(orderId, accountId, symbol, side, quantity, price, createdOn, "LIMIT", "SUBMIT");
    }
}
