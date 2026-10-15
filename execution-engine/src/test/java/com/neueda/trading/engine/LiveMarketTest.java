package com.neueda.trading.engine;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LiveMarketTest {
    final Instant now = Instant.parse("2026-10-15T15:00:00Z");
    final LiveMarket market = new LiveMarket(Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(60));
    LiveMarket.Quote quote(String bid, String ask, Instant time) {
        return new LiveMarket.Quote(UUID.randomUUID(), "ALPACA_IEX", "AAPL", new BigDecimal(bid), new BigDecimal(ask), time);
    }
    OrderEvent order(Side side, String type, String price) {
        return new OrderEvent(UUID.randomUUID(), "ACC-0001", "AAPL", side, 2, new BigDecimal(price), now, type, "SUBMIT");
    }
    @Test void marketBuyFillsAtLiveAskRatherThanReservationOrRandomImprovement() {
        var quote = quote("100.10", "100.20", now);
        market.update(quote);
        var fill = market.evaluate(order(Side.BUY, "MARKET", "95.00")).fill();
        assertEquals(new BigDecimal("100.20"), fill.price());
        assertEquals(quote.eventId(), fill.quoteEventId());
        assertEquals(now, fill.quoteTimestamp());
        assertEquals("ALPACA_IEX", fill.venue());
    }
    @Test void marketSellFillsAtLiveBid() {
        market.update(quote("100.10", "100.20", now));
        assertEquals(new BigDecimal("100.10"), market.evaluate(order(Side.SELL, "MARKET", "110")).fill().price());
    }
    @Test void limitsWaitUntilTheCorrectSideOfTheSpreadCrosses() {
        market.update(quote("99.00", "101.00", now));
        assertEquals("LIMIT_PRICE_NOT_REACHED", market.evaluate(order(Side.BUY, "LIMIT", "100")).reason());
        assertNull(market.evaluate(order(Side.SELL, "LIMIT", "100")).fill());
        market.update(quote("100.00", "100.00", now.plusSeconds(1)));
        assertNotNull(market.evaluate(order(Side.BUY, "LIMIT", "100")).fill());
        assertNotNull(market.evaluate(order(Side.SELL, "LIMIT", "100")).fill());
    }
    @Test void aBetterQuoteFillsAtTheQuoteRatherThanTheLimit() {
        market.update(quote("99.10", "99.20", now));
        assertEquals(new BigDecimal("99.20"), market.evaluate(order(Side.BUY, "LIMIT", "100")).fill().price());
    }
    @Test void missingAndStaleQuotesLeaveOrdersWaiting() {
        var order = order(Side.BUY, "MARKET", "100");
        assertEquals("WAITING_FOR_FRESH_ALPACA_QUOTE", market.evaluate(order).reason());
        market.update(quote("99", "100", now.minusSeconds(61)));
        assertNull(market.evaluate(order).fill());
    }
    @Test void demoAndFutureQuotesNeverBecomeExecutable() {
        var valid = quote("99", "100", now);
        market.update(new LiveMarket.Quote(valid.eventId(), "DEMO", "AAPL", valid.bid(), valid.ask(), now));
        assertNull(market.evaluate(order(Side.BUY, "MARKET", "100")).fill());
        market.update(quote("99", "100", now.plusSeconds(6)));
        assertNull(market.evaluate(order(Side.BUY, "MARKET", "100")).fill());
    }
    @Test void lateDeliveryCannotReplaceANewerQuote() {
        market.update(quote("100", "101", now));
        market.update(quote("90", "91", now.minusSeconds(1)));
        assertEquals(new BigDecimal("101.00"), market.evaluate(order(Side.BUY, "MARKET", "100")).fill().price());
    }
    @Test void invalidNewQuoteInvalidatesTheBookAndOlderQuotesCannotRestoreIt() {
        market.update(quote("100", "101", now.minusSeconds(1)));
        market.update(quote("100", "0", now));
        market.update(quote("90", "91", now.minusSeconds(1)));
        assertNull(market.evaluate(order(Side.BUY, "MARKET", "100")).fill());
    }
    @Test void centRoundingNeverBreaksALimit() {
        market.update(quote("99.999", "100.001", now));
        assertNull(market.evaluate(order(Side.BUY, "LIMIT", "100")).fill());
        assertNull(market.evaluate(order(Side.SELL, "LIMIT", "100")).fill());
        assertEquals(new BigDecimal("100.01"), market.evaluate(order(Side.BUY, "MARKET", "100")).fill().price());
        assertEquals(new BigDecimal("99.99"), market.evaluate(order(Side.SELL, "MARKET", "100")).fill().price());
    }
}
