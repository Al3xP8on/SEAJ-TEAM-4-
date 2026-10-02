package com.neueda.leap.messaging;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketDataPayload(
    String symbol,
    BigDecimal bidPrice,
    BigDecimal askPrice,
    BigDecimal lastPrice,
    Instant priceTimeStamp
) {
    public MarketDataPayload {
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

    public MarketPrice toMarketPrice() {
        return new MarketPrice(symbol, bidPrice, askPrice, lastPrice, priceTimeStamp);
    }
    
    public static MarketDataPayload fromMarketPrice(MarketPrice price) {
        return new MarketDataPayload(
            price.symbol(),
            price.bidPrice(),
            price.askPrice(),
            price.lastPrice(),
            price.priceTimeStamp()
        );
    }
}