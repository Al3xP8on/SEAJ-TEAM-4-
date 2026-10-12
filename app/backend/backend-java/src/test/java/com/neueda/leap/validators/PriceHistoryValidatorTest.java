package com.neueda.leap.validators;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriceHistoryValidatorTest {
    private final PriceHistoryValidator validator = new PriceHistoryValidator();
    @Test void normalizesSymbolsAndEnforcesLengthBoundary() {
        assertEquals("AAPL", validator.validateSymbol(" aapl "));
        assertEquals("A".repeat(20), validator.validateSymbol("A".repeat(20)));
        assertThrows(NullPointerException.class, () -> validator.validateSymbol(null));
        assertThrows(IllegalArgumentException.class, () -> validator.validateSymbol(" "));
        assertThrows(IllegalArgumentException.class, () -> validator.validateSymbol("A".repeat(21)));
    }
    @Test void acceptsPastAndPresentDatesAndRejectsFutureOrNullDates() {
        LocalDate today = LocalDate.now();
        assertEquals(today, validator.validateDate(today));
        assertEquals(today.minusDays(1), validator.validateDate(today.minusDays(1)));
        assertThrows(NullPointerException.class, () -> validator.validateDate(null));
        assertThrows(IllegalArgumentException.class, () -> validator.validateDate(today.plusDays(2)));
    }
    @Test void validatesNonnegativePricesAndVolumes() {
        assertEquals(BigDecimal.ZERO, validator.validatePrice(BigDecimal.ZERO, "Open"));
        assertEquals(BigDecimal.TEN, validator.validatePrice(BigDecimal.TEN, "Open"));
        assertEquals("Open price cannot be null", assertThrows(NullPointerException.class, () -> validator.validatePrice(null, "Open")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> validator.validatePrice(BigDecimal.ONE.negate(), "Open"));
        assertEquals(0, validator.validateVolume(0));
        assertEquals(Long.MAX_VALUE, validator.validateVolume(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> validator.validateVolume(-1));
    }
    @ParameterizedTest @CsvSource({"10,12,8,11", "10,10,10,10", "0,0,0,0"})
    void acceptsValidOhlcRelationships(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        assertDoesNotThrow(() -> validator.validateOHLCRelationships(open, high, low, close));
    }
    @ParameterizedTest @CsvSource({"10,8,9,10", "12,11,8,10", "10,11,8,12", "8,12,9,10", "10,12,9,8"})
    void rejectsEachInvalidOhlcRelationship(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        assertThrows(IllegalArgumentException.class, () -> validator.validateOHLCRelationships(open, high, low, close));
    }
}
