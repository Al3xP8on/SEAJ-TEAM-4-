package com.neueda.leap.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;

class LatestPriceStoreTest {
    private final LatestPriceStore store = new LatestPriceStore();
    private MarketPrice price(Instant time) {
        return new MarketPrice(" aapl ", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, time);
    }
    @Test void updatesReplaceExistingSymbolAndNormalizeLookups() {
        MarketPrice first = price(Instant.now().minusSeconds(10));
        MarketPrice latest = price(Instant.now());
        store.updatePrice(first);
        store.updatePrice(latest);
        assertSame(latest, store.getPrice(" aapl ").orElseThrow());
        assertEquals(1, store.getSize());
        assertTrue(store.getPrice("MSFT").isEmpty());
    }
    @Test void rejectsNullUpdatesAndHandlesInvalidLookups() {
        assertThrows(IllegalArgumentException.class, () -> store.updatePrice(null));
        for (String symbol : new String[]{null, "", "  "}) {
            assertTrue(store.getPrice(symbol).isEmpty());
            assertTrue(store.getPriceAge(symbol).isEmpty());
            assertFalse(store.removePrice(symbol));
        }
    }
    @Test void detectsStaleFreshAndFuturePrices() {
        assertTrue(store.isStale(null));
        assertFalse(store.isNotStale(null));
        assertTrue(store.isStale(price(Instant.now().minusSeconds(120))));
        assertTrue(store.isNotStale(price(Instant.now())));
        assertTrue(store.isStale(price(Instant.now().plusSeconds(120))));
    }
    @Test void reportsAgeAndRemovesPrices() {
        store.updatePrice(price(Instant.now().minusSeconds(120)));
        assertTrue(store.getPriceAge("AAPL").orElseThrow() >= 120);
        assertTrue(store.removePrice(" aapl "));
        assertFalse(store.removePrice("AAPL"));
        assertTrue(store.getPriceAge("AAPL").isEmpty());
        store.updatePrice(price(Instant.now()));
        store.clearAll();
        assertEquals(0, store.getSize());
        assertTrue(store.getPriceIfNotStale("AAPL").isEmpty());
    }
    @Test void doesNotUseStalePricesForOrders() {
        MarketPrice stale = price(Instant.now().minusSeconds(120));
        store.updatePrice(stale);
        assertTrue(store.getPriceIfNotStale("AAPL").isEmpty());
    }
}
