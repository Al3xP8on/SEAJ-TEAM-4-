package com.neueda.leap;

import com.neueda.leap.models.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

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
                () -> assertEquals("Equity", instrument.getAssetClass()),
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
            assertEquals("Symbol can't be empty", exception.getMessage());

        }
        @Test
        @DisplayName("Reject null symbol")
        void rejectNullSymbol() {
            NullPointerException exception = assertThrows(NullPointerException.class, () -> {
                new Instrument(null, "Apple Inc.", "Equity", "USD", true);
            });
            assertEquals("Symbol can't be null", exception.getMessage());
        }

        @Test 
        @DisplayName ("Reject symbols > 20 characters")
        void rejectLongSymbols() {
            String longSymbol = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new Instrument(longSymbol, "Apple Inc.", "Equity", "USD", true);
            });
            assertEquals("Symbol can't be longer than 20 characters", exception.getMessage());
        }

        @Test
        @DisplayName("Allow valid symbols")
        void allowValidSymbols() {
            String validSymbol = "A".repeat(20);
            Instrument result = new Instrument(validSymbol, "Apple Inc.", "Equity", "USD", true);
            assertEquals(validSymbol, result.getSymbol());
        }

        @Test
        @DisplayName("Trim whitespace from symbols")
        void trimWhitespaceFromSymbols() {
            Instrument result = new Instrument("  AAPL  ", "Apple Inc.", "Equity", "USD", true);
            assertEquals("AAPL", result.getSymbol());
        }
    }

    @Nested 
    @DisplayName("Name Validation")
    class nameValidationTests{
        
        @Test
        @DisplayName("Reject null name")
        void rejectNullName() {
            NullPointerException exception = assertThrows(NullPointerException.class, () -> {
                new Instrument("AAPL", null, "Equity", "USD", true);
            });
            assertEquals("Name can't be null", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "  ", "\t"})
        @DisplayName("Reject empty names")
        void rejectEmptyNames(String invalidName) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new Instrument("AAPL", invalidName, "Equity", "USD", true);
            });
            assertEquals("Name can't be empty", exception.getMessage());
        }

        @Test
        @DisplayName("Reject names > 255 characters")
        void rejectLongNames() {
            String longName = "A".repeat(256);
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new Instrument("AAPL", longName, "Equity", "USD", true);
            });
            assertEquals("Name can't be longer than 255 characters", exception.getMessage());
        }

        @Test
        @DisplayName("Allow valid names")
        void allowValidNames() {
            String validName = "A".repeat(255);
            Instrument result = new Instrument("AAPL", validName, "Equity", "USD", true);
            assertEquals(validName, result.getName());
        }

        @Test
        @DisplayName("Trim whitespace from names")
        void trimWhitespaceFromNames() {
            Instrument result = new Instrument("AAPL", "  Apple Inc.  ", "Equity", "USD", true);
            assertEquals("Apple Inc.", result.getName());
        }
    }

    @Nested 
    @DisplayName("Asset Class Validation")
    class assetClassValidationTests{

        @Test
        @DisplayName("Reject null asset class")
        void rejectNullAssetClass() {
            NullPointerException exception = assertThrows(NullPointerException.class, () -> {
                new Instrument("AAPL", "Apple Inc.", null, "USD", true);
            });
            assertEquals("Asset class can't be null", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "  ", "\t"})
        @DisplayName("Reject empty asset classes")
        void rejectEmptyAssetClasses(String invalidAssetClass) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new Instrument("AAPL", "Apple Inc.", invalidAssetClass, "USD", true);
            });
            assertEquals("Asset class can't be empty", exception.getMessage());
        }

        @Test
        @DisplayName("Reject asset classes > 50 characters")
        void rejectLongAssetClasses() {
            String longAssetClass = "A".repeat(51);
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new Instrument("AAPL", "Apple Inc.", longAssetClass, "USD", true);
            });
            assertEquals("Asset class can't be longer than 50 characters", exception.getMessage());
        }

        @Test
        @DisplayName("Allow valid asset classes")
        void allowValidAssetClasses() {
            String validAssetClass = "A".repeat(50);
            Instrument result = new Instrument("AAPL", "Apple Inc.", validAssetClass, "USD", true);
            assertEquals(validAssetClass, result.getAssetClass());
        }

        @Test
        @DisplayName("Trim whitespace from asset classes")
        void trimWhitespaceFromAssetClasses() {
            Instrument result = new Instrument("AAPL", "Apple Inc.", "  Equity  ", "USD", true);
            assertEquals("Equity", result.getAssetClass());
        }
    }

    @Nested
    @DisplayName("Currency Validation")
    class currencyValidationTests{

        @Test
        @DisplayName("Reject null currency")
        void rejectNullCurrency() {
            NullPointerException exception = assertThrows(NullPointerException.class, () -> {
                new Instrument("AAPL", "Apple Inc.", "Equity", null, true);
            });
            assertEquals("Currency can't be null", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "U", "US", "USDA", "  ", "\t"})
        @DisplayName("Reject currency not exactly 3 characters")
        void rejectInvalidLengthCurrency(String invalidCurrency) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new Instrument("AAPL", "Apple Inc.", "Equity", invalidCurrency, true);
            });
            assertEquals("Currency must be exactly 3 characters", exception.getMessage());
        }

        @Test
        @DisplayName("Allow valid 3-letter currency codes")
        void allowValidCurrency() {
            Instrument result = new Instrument("AAPL", "Apple Inc.", "Equity", "usd", true);
            assertEquals("USD", result.getCurrency());
        }

        @Test
        @DisplayName("Convert currency to uppercase")
        void convertCurrencyToUppercase() {
            Instrument result = new Instrument("AAPL", "Apple Inc.", "Equity", "eur", true);
            assertEquals("EUR", result.getCurrency());
        }

        @Test
        @DisplayName("Trim whitespace from currency")
        void trimWhitespaceFromCurrency() {
            Instrument result = new Instrument("AAPL", "Apple Inc.", "Equity", "  GBP  ", true);
            assertEquals("GBP", result.getCurrency());
        }
    }
}
 