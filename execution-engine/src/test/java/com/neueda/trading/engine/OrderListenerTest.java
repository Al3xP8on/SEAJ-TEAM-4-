package com.neueda.trading.engine;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderListenerTest {
    final Instant now = Instant.parse("2026-10-15T15:00:00Z");
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    @SuppressWarnings("unchecked") final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    final LiveMarket market = new LiveMarket(Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(60));
    final OrderListener listener = new OrderListener(market, kafka, mapper, "executions", "order-lifecycle");
    OrderEvent order(String action) { return new OrderEvent(UUID.randomUUID(), "ACC-0001", "AAPL", Side.BUY, 2, new BigDecimal("100.00"), now, "LIMIT", action); }
    OrderListenerTest() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }
    void quote(String ask, int seconds) throws Exception {
        listener.onMarketData(mapper.writeValueAsString(Map.of("eventId", UUID.randomUUID(), "eventType", "PRICE_UPDATE",
            "payload", Map.of("source", "ALPACA_IEX", "symbol", "AAPL", "bidPrice", new BigDecimal(ask).subtract(new BigDecimal("0.10")),
                "askPrice", new BigDecimal(ask), "priceTimeStamp", now.plusSeconds(seconds)))));
    }
    @Test void restingLimitFillsOnALaterKafkaQuoteAtTheActualAsk() throws Exception {
        var order = order("SUBMIT");
        listener.onOrder(mapper.writeValueAsString(order));
        quote("101.00", 0);
        verify(kafka, never()).send(eq("executions"), anyString(), anyString());
        quote("99.50", 1);
        var capture = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("executions"), eq(order.accountId()), capture.capture());
        var fill = mapper.readValue(capture.getValue(), ExecutionEvent.class);
        assertEquals(new BigDecimal("99.50"), fill.price());
        assertEquals(order.orderId(), fill.orderId());
        assertEquals(now.plusSeconds(1), fill.quoteTimestamp());
    }
    @Test void cancelledOrderDoesNotFillWhenItsLimitIsLaterReached() throws Exception {
        var order = order("SUBMIT");
        listener.onOrder(mapper.writeValueAsString(order));
        var cancel = new OrderEvent(order.orderId(), order.accountId(), order.symbol(), order.side(), order.quantity(), order.price(), now, "LIMIT", "CANCEL");
        listener.onOrder(mapper.writeValueAsString(cancel));
        quote("99.50", 1);
        listener.onOrder(mapper.writeValueAsString(order));
        verify(kafka, never()).send(eq("executions"), anyString(), anyString());
    }
    @Test void duplicateCommandsReuseTheExactExecutionIdentityAndPrice() throws Exception {
        quote("99.50", 0);
        var order = order("SUBMIT");
        listener.onOrder(mapper.writeValueAsString(order));
        quote("98.00", 1);
        listener.onOrder(mapper.writeValueAsString(order));
        var capture = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(2)).send(eq("executions"), anyString(), capture.capture());
        assertEquals(capture.getAllValues().get(0), capture.getAllValues().get(1));
    }
    @Test void failedFillPublicationIsRetriedWithoutRepricingTheOrder() throws Exception {
        quote("99.50", 0);
        when(kafka.send(eq("executions"), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")))
            .thenReturn(CompletableFuture.completedFuture(null));
        var order = order("SUBMIT");
        assertThrows(IllegalStateException.class, () -> listener.onOrder(mapper.writeValueAsString(order)));
        quote("98.00", 1);
        var capture = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(2)).send(eq("executions"), anyString(), capture.capture());
        assertEquals(capture.getAllValues().get(0), capture.getAllValues().get(1));
    }
    @Test void malformedMessagesPropagateToTheDeadLetterHandler() {
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> listener.onOrder("not-json"));
        verifyNoInteractions(kafka);
    }
}
