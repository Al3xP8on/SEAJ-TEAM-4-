package com.neueda.leap.models;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolPriceTest {
    @Test
    void equivalentPricesShareHashCodeAndDeduplicateInHashSet() {
        SymbolPrice first = new SymbolPrice("AAPL", new BigDecimal("150.00"));
        SymbolPrice equivalent = new SymbolPrice("AAPL", new BigDecimal("150.00"));
        ReflectionTestUtils.setField(equivalent, "timestamp", first.getTimestamp());

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());

        Set<SymbolPrice> prices = new HashSet<>();
        prices.add(first);
        assertTrue(prices.contains(equivalent));
        assertFalse(prices.add(equivalent));
        assertEquals(1, prices.size());
    }

    @Test
    void pricesWithDifferentScaleOrTimestampRemainDistinctInHashSet() {
        ZonedDateTime timestamp = ZonedDateTime.parse("2026-10-12T08:00:00Z");
        SymbolPrice first = priceAt("150.00", timestamp);
        SymbolPrice differentScale = priceAt("150.0", timestamp);
        SymbolPrice laterPrice = priceAt("150.00", timestamp.plusSeconds(1));

        Set<SymbolPrice> prices = new HashSet<>();
        prices.add(first);
        assertTrue(prices.add(differentScale));
        assertTrue(prices.add(laterPrice));
        assertEquals(3, prices.size());
    }

    private SymbolPrice priceAt(String price, ZonedDateTime timestamp) {
        SymbolPrice symbolPrice = new SymbolPrice("AAPL", new BigDecimal(price));
        ReflectionTestUtils.setField(symbolPrice, "timestamp", timestamp);
        return symbolPrice;
    }
}
