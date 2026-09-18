package com.neueda.leap;

import java.math.BigDecimal;

import com.neueda.leap.models.SymbolPrice;
import com.neueda.leap.services.SymbolPriceUpdater;

import com.neueda.leap.exceptions.InvalidSymbolPriceException;
import com.neueda.leap.exceptions.InvalidPriceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class SymbolPriceUpdaterTest {
    private SymbolPriceUpdater symbolPriceUpdater;

    @BeforeEach
    void setUp(){
        symbolPriceUpdater = new SymbolPriceUpdater();
    }

    @Nested 
    public class UpdateSymbolPriceExceptionTests{

        @Test
        @DisplayName("throws InvalidSymbolPriceException when symbolPrice is null")
        void testInvalidSymbolPriceExceptionThrowsWhenSymbolPriceIsNull(){
            SymbolPrice symbolPrice = null;
            assertThrows(
                InvalidSymbolPriceException.class,
                () -> symbolPriceUpdater.updateSymbolPrice(symbolPrice, BigDecimal.valueOf(100))
            );
        }

        @Test
        @DisplayName("throws InvalidPriceException when newPrice is null")
        void testInvalidPriceExceptionThrowsWhenNewPriceIsNull(){
            SymbolPrice symbolPrice = new SymbolPrice("AAPL", BigDecimal.valueOf(100));
            assertThrows(
                InvalidPriceException.class,
                () -> symbolPriceUpdater.updateSymbolPrice(symbolPrice, null)
            );
        }

    }

    @Test
    @DisplayName("successfully updates the symbol price")
    void testSuccessfulUpdateOfSymbolPrice(){
        try{ 
            SymbolPrice symbolPrice = new SymbolPrice("AAPL", BigDecimal.valueOf(100));
            SymbolPrice updatedSymbolPrice = symbolPriceUpdater.updateSymbolPrice(symbolPrice, BigDecimal.valueOf(200));

            assertEquals(BigDecimal.valueOf(200), updatedSymbolPrice.getPrice());
            assertEquals("AAPL", updatedSymbolPrice.getSymbol());
            assertNotNull(updatedSymbolPrice.getTimestamp());
            assertNotNull(updatedSymbolPrice);

        } catch (InvalidSymbolPriceException | InvalidPriceException e) {
            fail("Exception should not have been thrown: " + e.getMessage()); // - just in case something unexpected occurs, act as a fallback & fail test.
        }
    }
    
}
