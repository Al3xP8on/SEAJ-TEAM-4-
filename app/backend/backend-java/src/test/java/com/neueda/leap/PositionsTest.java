package com.neueda.leap;

import com.neueda.leap.models.Positions;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

@DisplayName("Positions Tests")
class PositionsTest {
    
    private Positions position;

    @BeforeEach
    void setUp() {
        position = new Positions(1L, "AAPL", 100, new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("Create position")
    void testCreatePosition() {
        assertAll(
            () -> assertEquals(1L, position.getAccountId()),
            () -> assertEquals("AAPL", position.getSymbol()),
            () -> assertEquals(100, position.getQuantity()),
            () -> assertEquals(new BigDecimal("150.00"), position.getAverageCost())
        );
    }

    @Test
    @DisplayName("Calculate market value")
    void testMarketValue() {
        BigDecimal marketValue = position.marketValue(new BigDecimal("160.00"));
        assertEquals(new BigDecimal("16000.00"), marketValue);
    }

    @Test
    @DisplayName("Apply positive quantity change")
    void testApplyPositiveQuantityChange() throws InsufficientHoldingsException {
        position.apply(50, new BigDecimal("160.00"));
        
        assertAll(
            () -> assertEquals(150, position.getQuantity()),
            () -> assertEquals(new BigDecimal("153.33"), position.getAverageCost())
        );
    }

    @Test
    @DisplayName("Apply negative quantity change")
    void testApplyNegativeQuantityChange() throws InsufficientHoldingsException {
        position.apply(-30, new BigDecimal("160.00"));
        assertEquals(70, position.getQuantity());
    }

    @Test
    @DisplayName("Apply zero quantity change")
    void testApplyZeroQuantity() throws InsufficientHoldingsException {
        position.apply(-100, new BigDecimal("160.00"));
        
        assertAll(
            () -> assertEquals(0, position.getQuantity()),
            () -> assertEquals(BigDecimal.ZERO, position.getAverageCost())
        );
    }

}
