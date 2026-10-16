package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Matches orders only against timestamped Alpaca quotes received through Kafka. */
public class LiveMarket {
    public record Quote(UUID eventId, String source, String symbol, BigDecimal bid,
                        BigDecimal ask, Instant timestamp) { }
    public record Decision(ExecutionEvent fill, String reason, Quote quote) { }
    private final Map<String, Quote> quotes = new HashMap<>();
    private final Map<String, Instant> watermarks = new HashMap<>();
    private final Clock clock;
    private final Duration maxAge;

    public LiveMarket(Clock clock, Duration maxAge) {
        this.clock = clock;
        this.maxAge = maxAge;
    }

    public synchronized void update(Quote quote) {
        if (quote == null || quote.eventId() == null || quote.symbol() == null || quote.timestamp() == null
                || quote.source() == null
                || !Set.of("ALPACA_IEX", "ALPACA_SIP").contains(quote.source())) return;
        Instant previous = watermarks.get(quote.symbol());
        if (previous != null && !quote.timestamp().isAfter(previous)) return;
        if (quote.timestamp().isAfter(clock.instant().plusSeconds(5))) return;
        watermarks.put(quote.symbol(), quote.timestamp());
        if (quote.bid() == null || quote.ask() == null || quote.bid().signum() <= 0
                || quote.ask().compareTo(quote.bid()) < 0) {
            quotes.remove(quote.symbol());
            return;
        }
        quotes.put(quote.symbol(), quote);
    }

    public synchronized Decision evaluate(OrderEvent order) {
        Quote quote = quotes.get(order.symbol());
        Instant now = clock.instant();
        if (quote == null || Duration.between(quote.timestamp(), now).compareTo(maxAge) > 0)
            return new Decision(null, "WAITING_FOR_FRESH_ALPACA_QUOTE", quote);
        BigDecimal price = (order.side() == Side.BUY ? quote.ask() : quote.bid()).setScale(2,
                order.side() == Side.BUY ? RoundingMode.CEILING : RoundingMode.FLOOR);
        if (price.signum() <= 0) return new Decision(null, "WAITING_FOR_FRESH_ALPACA_QUOTE", quote);
        if (order.orderType().equals("LIMIT") && (order.side() == Side.BUY
                ? price.compareTo(order.price()) > 0 : price.compareTo(order.price()) < 0))
            return new Decision(null, "LIMIT_PRICE_NOT_REACHED", quote);
        return new Decision(new ExecutionEvent(UUID.randomUUID(), order.orderId(), order.accountId(),
                order.symbol(), order.side(), order.quantity(), price, order.price(), quote.source(), now,
                quote.timestamp(), quote.eventId(), quote.bid(), quote.ask()), null, quote);
    }
}
