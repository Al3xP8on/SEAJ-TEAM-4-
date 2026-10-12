package com.neueda.leap.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.messaging.*;
import com.neueda.leap.exceptions.InvalidSymbolException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.mockito.Mockito.*;

class MarketPriceServiceTest {
    private final LatestPriceStore store = mock(LatestPriceStore.class);
    private final MarketPriceService service = new MarketPriceService(store);
    @Test void normalizesSymbolsAndCalculatesMidPriceAndSpread() {
        MarketPrice price = new MarketPrice("AAPL", new BigDecimal("10.00"), new BigDecimal("10.03"), BigDecimal.TEN, Instant.now());
        when(store.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(price));
        assertSame(price, service.getLatestPrice(" aapl ").orElseThrow());
        assertEquals(new BigDecimal("10.02"), service.getMidPrice("aapl").orElseThrow());
        assertEquals(new BigDecimal("0.03"), service.getSpread("aapl").orElseThrow());
        assertTrue(service.isPriceAvailable("aapl"));
        assertSame(price, service.getPriceForExecution("aapl"));
    }
    @Test void missingPricesProduceEmptyResultsAndRejectExecution() {
        when(store.getPriceIfNotStale("AAPL")).thenReturn(Optional.empty());
        assertTrue(service.getMidPrice("AAPL").isEmpty());
        assertTrue(service.getSpread("AAPL").isEmpty());
        assertFalse(service.isPriceAvailable("AAPL"));
        assertEquals("No fresh price available for symbol: AAPL", assertThrows(InvalidSymbolException.class,
                () -> service.getPriceForExecution(" aapl ")).getMessage());
    }
    @Test void rejectsInvalidSymbolsWithoutConsultingStore() {
        for (String symbol : new String[]{null, "", "  "}) {
            assertThrows(IllegalArgumentException.class, () -> service.getLatestPrice(symbol));
            assertThrows(IllegalArgumentException.class, () -> service.getPriceForExecution(symbol));
            assertTrue(service.getPriceAge(symbol).isEmpty());
        }
        verifyNoInteractions(store);
    }
    @Test void normalizesAgeLookup() {
        when(store.getPriceAge("AAPL")).thenReturn(Optional.of(12L));
        assertEquals(Optional.of(12L), service.getPriceAge(" aapl "));
    }
}
