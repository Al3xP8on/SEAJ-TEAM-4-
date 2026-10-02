package com.neueda.leap.messaging;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a market price snapshot for a financial instrument.
 * Used to transfer price data from external market data sources.
 */
public record MarketPrice(
    String symbol,
    BigDecimal bidPrice,
    BigDecimal askPrice,
    BigDecimal lastPrice,
    Instant priceTimeStamp
) {
    public MarketPrice {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (bidPrice == null || askPrice == null || lastPrice == null) {
            throw new IllegalArgumentException("Prices cannot be null");
        }
        if (priceTimeStamp == null) {
            throw new IllegalArgumentException("Price timestamp cannot be null");
        }
        symbol = symbol.trim().toUpperCase();
    }
}