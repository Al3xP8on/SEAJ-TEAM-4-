package com.neueda.leap.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UtilsTest {
    @Test void calculatesTotalValueWithoutRoundingOrLongOverflow() {
        assertEquals(new BigDecimal("37.0370367"), Utils.calculateTotalValue(new BigDecimal("12.3456789"), 3));
        assertEquals(BigDecimal.ZERO, Utils.calculateTotalValue(BigDecimal.ONE, 0));
        assertEquals(BigDecimal.valueOf(Long.MAX_VALUE), Utils.calculateTotalValue(BigDecimal.ONE, Long.MAX_VALUE));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"A", "ABCD"})
    void fullyMasksShortSensitiveIds(String value) {
        assertEquals("***", Utils.maskSensitiveId(value));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"A", "12345678"})
    void fullyMasksShortUuids(String value) {
        assertEquals("****", Utils.maskUUID(value));
    }
    @Test void masksLongIdentifiers() {
        assertEquals("ACC1-***", Utils.maskSensitiveId("ACC123456"));
        assertEquals("12345678-****", Utils.maskUUID("123456789"));
    }
}
