package com.neueda.trading.engine;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Each engine decision is logged once, without the account id, and quotes alone log nothing. */
class OrderListenerLoggingTest {
    final Instant now = Instant.parse("2026-10-15T15:00:00Z");
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    @SuppressWarnings("unchecked") final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    final LiveMarket market = new LiveMarket(Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(60));
    final OrderListener listener = new OrderListener(market, kafka, mapper, "executions", "order-lifecycle");
    final Logger logger = (Logger) LoggerFactory.getLogger(OrderListener.class);
    final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    OrderListenerLoggingTest() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach void detach() { logger.detachAppender(logs); }

    OrderEvent order(String symbol, String action) {
        return new OrderEvent(UUID.randomUUID(), "ACC-0001", symbol, Side.BUY, 2, new BigDecimal("100.00"), now, "LIMIT", action);
    }

    void quote(String ask, int seconds) throws Exception {
        listener.onMarketData(mapper.writeValueAsString(Map.of("eventId", UUID.randomUUID(), "eventType", "PRICE_UPDATE",
            "payload", Map.of("source", "ALPACA_IEX", "symbol", "AAPL", "bidPrice", new BigDecimal(ask).subtract(new BigDecimal("0.10")),
                "askPrice", new BigDecimal(ask), "priceTimeStamp", now.plusSeconds(seconds)))));
    }

    List<String> lines() { return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList(); }

    @Test void logsReceivedWaitingAndFilledOnceEach() throws Exception {
        var order = order("AAPL", "SUBMIT");
        listener.onOrder(mapper.writeValueAsString(order));
        quote("101.00", 1);
        quote("101.20", 2); // still above the limit: same reason, so no second waiting line
        quote("99.50", 3);
        var lines = lines();
        assertEquals(1, lines.stream().filter(l -> l.startsWith("Order received orderId=" + order.orderId())).count());
        assertEquals(1, lines.stream().filter(l -> l.contains("reason=LIMIT_PRICE_NOT_REACHED")).count());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Order filled orderId=" + order.orderId()) && l.contains("price=99.50")));
        assertTrue(lines.stream().noneMatch(l -> l.contains("ACC-0001")), "account ids stay out of the logs");
    }

    @Test void logsCancellationsAndWhetherTheOrderWasResting() throws Exception {
        var order = order("AAPL", "SUBMIT");
        listener.onOrder(mapper.writeValueAsString(order));
        listener.onOrder(mapper.writeValueAsString(new OrderEvent(order.orderId(), order.accountId(), "AAPL", Side.BUY, 2, order.price(), now, "LIMIT", "CANCEL")));
        assertTrue(lines().contains("Order cancel orderId=" + order.orderId() + " symbol=AAPL wasResting=true"));
    }

    @Test void stripsLineBreaksFromSymbols() throws Exception {
        listener.onOrder(mapper.writeValueAsString(order("AAPL\nFAKE log line", "SUBMIT")));
        assertTrue(lines().stream().allMatch(l -> !l.contains("\n")));
    }

    @Test void quotesWithoutOrdersLogNothing() throws Exception {
        quote("99.50", 1);
        assertTrue(logs.list.isEmpty());
    }
}
