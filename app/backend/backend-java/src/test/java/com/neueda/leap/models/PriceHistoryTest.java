package com.neueda.leap.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriceHistoryTest {
    private PriceHistory history(String open, String close) {
        return new PriceHistory(" aapl ", LocalDate.of(2026, 1, 1), new BigDecimal(open), new BigDecimal("12"), new BigDecimal("0"), new BigDecimal(close), 100);
    }
    @ParameterizedTest @CsvSource({"10,12,2,20.00,true,false,false", "10,8,-2,-20.00,false,true,false", "10,10,0,0.00,false,false,true", "0,1,1,0,true,false,false", "3,4,1,33.33,true,false,false"})
    void calculatesRangeDailyChangePercentageAndDirection(String open, String close, BigDecimal change, BigDecimal percent, boolean bullish, boolean bearish, boolean neutral) {
        PriceHistory history = history(open, close);
        assertEquals(new BigDecimal("12"), history.getPriceRange());
        assertEquals(change, history.getDailyChange());
        assertEquals(percent, history.getDailyChangePercent());
        assertEquals(bullish, history.isBullish());
        assertEquals(bearish, history.isBearish());
        assertEquals(neutral, history.isNeutral());
    }
    @Test void identifiesHistoryByNormalizedSymbolAndDate() {
        PriceHistory first = history("10", "12");
        PriceHistory sameDay = history("10", "8");
        assertEquals(first, sameDay);
        assertEquals(sameDay, first);
        assertEquals(first.hashCode(), sameDay.hashCode());
        sameDay.setPriceDate(LocalDate.of(2026, 1, 2));
        assertNotEquals(first, sameDay);
        sameDay.setPriceDate(first.getPriceDate());
        sameDay.setSymbol("MSFT");
        assertNotEquals(first, sameDay);
        assertNotEquals(first, null);
        Object differentType = "AAPL";
        assertFalse(first.equals(differentType));
    }
    @Test void defaultConstructorSupportsValidatedUpdates() {
        PriceHistory history = new PriceHistory();
        history.setSymbol(" aapl ");
        history.setPriceDate(LocalDate.of(2026, 1, 1));
        history.setOpen(BigDecimal.TEN);
        history.setHigh(new BigDecimal("12"));
        history.setLow(BigDecimal.ONE);
        history.setClose(new BigDecimal("11"));
        history.setVolume(100);
        history.setId(1L);
        assertEquals("AAPL", history.getSymbol());
        assertEquals(LocalDate.of(2026, 1, 1), history.getPriceDate());
        assertEquals(BigDecimal.TEN, history.getOpen());
        assertEquals(new BigDecimal("12"), history.getHigh());
        assertEquals(BigDecimal.ONE, history.getLow());
        assertEquals(new BigDecimal("11"), history.getClose());
        assertEquals(100, history.getVolume());
        assertEquals(1L, history.getId());
        assertNotNull(history.getValidator());
        assertThrows(IllegalArgumentException.class, () -> history.setVolume(-1));
        assertThrows(IllegalArgumentException.class, () -> history.setClose(BigDecimal.ONE.negate()));
    }
    @Test void rejectsMissingValidatorAndInvalidOhlcAtConstruction() {
        assertThrows(NullPointerException.class, () -> new PriceHistory("AAPL", LocalDate.of(2026, 1, 1), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new PriceHistory("AAPL", LocalDate.of(2026, 1, 1), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, 0));
    }
}
