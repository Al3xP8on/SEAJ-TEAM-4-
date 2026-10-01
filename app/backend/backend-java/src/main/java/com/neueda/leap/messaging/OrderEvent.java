package com.neueda.leap.messaging;

import com.neueda.leap.enums.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message on the {@code orders} topic: an accepted order the execution
 * engine should work. Keyed by {@code accountId}, so all orders for one
 * account land on the same partition and are worked in order.
 */
public record OrderEvent(
        UUID orderId,
        String accountId,
        String symbol,
        OrderSide side,
        int quantity,
        BigDecimal price,
        Instant createdOn
) {
}
