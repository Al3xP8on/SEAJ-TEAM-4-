package com.neueda.leap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

import java.beans.Transient;

import main.java.com.neueda.leap.Instrument;

@DisplayName("Instrument Tests")
class InstrumentTest {

    private Instrument instrument;

    @BeforeEach
    void setUp() {
        instrument = new Instrument("AAPL", "Apple Inc.", "Equity", "USD", true);
    }

    @Nested
    @DisplayName("Valid Instruments")
    class validInstrumentTests{

        @Test
        @DisplayName("Should create an Instrument successfully")
        void successfulInstrument(){
            assertAll(
                () -> assertEquals("AAPL", instrument.getSymbol()),
                () -> assertEquals("Apple Inc.", instrument.getName()),
                () -> assertEquals("Equity", instrument.getType()),
                () -> assertEquals("USD", instrument.getCurrency()),
                () -> assertTrue(instrument.isTradable())
            );
        }

        @Test
        @DisplayName("Not Tradable Instrument")
        void notTradableInstrument(){
            
            Instrument nonTradable = new Instrument("AAPL", "Apple Inc.", "Equity", "USD", false);

            assertFalse(nonTradable.isTradable());
        }

    }

    @Nested
    @DisplayName("Symbol Validation")
    class symbolValidationTests{

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "  ", "\t"})
        @DisplayName("Reject empty symbols")
        void rejectEmptySymbols(String invalidSymbol) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new Instrument(invalidSymbol, "Apple Inc.", "Equity", "USD", true);
            });
            assertEquals("Symbol cannot be empty", exception.getMessage());

        }
    }



}
 