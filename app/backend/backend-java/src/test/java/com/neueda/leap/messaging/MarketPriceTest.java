package com.neueda.leap.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;

class MarketPriceTest {
    @Test void normalizesSymbolAndRoundTripsPayloadWithoutLosingPrecision() {
        MarketPrice price = new MarketPrice(" aapl ", new BigDecimal("123.456789"), new BigDecimal("124.000001"), BigDecimal.TEN, Instant.parse("2026-01-01T00:00:00Z"));
        assertEquals("AAPL", price.symbol());
        assertEquals(price, MarketDataPayload.fromMarketPrice(price).toMarketPrice());
        assertEquals("MSFT", new MarketDataPayload(" msft ", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, Instant.now()).symbol());
    }
    @Test void rejectsMissingRequiredFieldsInPriceAndPayload() {
        for (String symbol : new String[]{null, ""}) {
            assertThrows(IllegalArgumentException.class, () -> new MarketPrice(symbol, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, Instant.now()));
            assertThrows(IllegalArgumentException.class, () -> new MarketDataPayload(symbol, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, Instant.now()));
        }
        for (int missing = 0; missing < 4; missing++) {
            BigDecimal bid = missing == 0 ? null : BigDecimal.ONE;
            BigDecimal ask = missing == 1 ? null : BigDecimal.ONE;
            BigDecimal last = missing == 2 ? null : BigDecimal.ONE;
            Instant time = missing == 3 ? null : Instant.now();
            assertThrows(IllegalArgumentException.class, () -> new MarketPrice("AAPL", bid, ask, last, time));
            assertThrows(IllegalArgumentException.class, () -> new MarketDataPayload("AAPL", bid, ask, last, time));
        }
    }
}
