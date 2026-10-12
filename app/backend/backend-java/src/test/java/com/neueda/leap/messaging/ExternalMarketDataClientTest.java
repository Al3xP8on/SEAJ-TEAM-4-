package com.neueda.leap.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;

class ExternalMarketDataClientTest {
    private final ExternalMarketDataClient client = new ExternalMarketDataClient();
    @Test void rejectsInvalidSymbols() {
        for (String symbol : new String[]{null, "", "  "})
            assertThrows(IllegalArgumentException.class, () -> client.getLatestMarketPrice(symbol));
    }
    @Test void returnsNormalizedSymbolAndProviderSnapshot() {
        Instant before = Instant.now();
        MarketPrice price = client.getLatestMarketPrice(" aapl ");
        assertEquals("AAPL", price.symbol());
        assertEquals(new BigDecimal("100.50"), price.bidPrice());
        assertEquals(new BigDecimal("100.55"), price.askPrice());
        assertEquals(new BigDecimal("100.52"), price.lastPrice());
        assertFalse(price.priceTimeStamp().isBefore(before));
        assertFalse(price.priceTimeStamp().isAfter(Instant.now()));
    }
}
