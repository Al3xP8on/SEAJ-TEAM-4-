package com.neueda.leap.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingDecisionTest {
    @ParameterizedTest @CsvSource({"BUY,9,true", "buy,10,true", "BUY,11,false", "SELL,11,true", "sell,10,true", "SELL,9,false"})
    void respectsBuyAndSellLimitBoundaries(String side, BigDecimal quote, boolean executable) {
        PricingDecision decision = PricingDecision.decide(BigDecimal.TEN, side, quote, true);
        assertEquals(executable, decision.canExecute());
        if (executable) {
            assertSame(quote, decision.executionPrice());
            assertEquals(side.toUpperCase() + " execution at quote", decision.reason());
        } else {
            assertNull(decision.executionPrice());
            assertEquals(side.equals("BUY") ? "BUY quote exceeds order limit: quote=11, limit=10" : "SELL quote below order limit: quote=9, limit=10", decision.reason());
        }
    }
    @Test void rejectsUntradableInstrumentBeforeCheckingPrices() {
        assertEquals(new PricingDecision(false, "Instrument not tradeable", null), PricingDecision.decide(null, null, null, false));
    }
    @Test void rejectsMissingPrices() {
        assertEquals(new PricingDecision(false, "Missing price data", null), PricingDecision.decide(null, "BUY", BigDecimal.TEN, true));
        assertEquals(new PricingDecision(false, "Missing price data", null), PricingDecision.decide(BigDecimal.TEN, "SELL", null, true));
    }
    @Test void rejectsUnknownAndNullSides() {
        for (String side : new String[]{null, "HOLD", ""})
            assertEquals(new PricingDecision(false, "Unknown order side: " + side, null), PricingDecision.decide(BigDecimal.TEN, side, BigDecimal.TEN, true));
    }
}
