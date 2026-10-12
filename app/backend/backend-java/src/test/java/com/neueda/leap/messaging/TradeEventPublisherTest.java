package com.neueda.leap.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.mockito.Mockito.*;

class TradeEventPublisherTest {
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, EventEnvelope> kafka = mock(KafkaTemplate.class);
    private final TradeEventPublisher publisher = new TradeEventPublisher(kafka);
    private TradeEvent event(String status) {
        return new TradeEvent(UUID.randomUUID(), "ACC-001", "AAPL", "BUY", new BigDecimal("10.25"), 3L, status, "IOC", LocalDateTime.of(2026, 1, 1, 12, 0));
    }
    @ParameterizedTest
    @CsvSource({"ACCEPTED,ORDER_ACCEPTED", "EXECUTED,TRADE_EXECUTED", "FILLED,ORDER_FILLED", "REJECTED,ORDER_REJECTED", "CANCELLED,ORDER_CANCELLED", "INITIATED,ORDER_PLACED", "OTHER,ORDER_PLACED"})
    void mapsStatusesAndPublishesCompletePayloadKeyedByAccount(String status, EventType expectedType) {
        ReflectionTestUtils.setField(publisher, "tradeEventsTopic", "trade-events");
        TradeEvent event = event(status);
        event.setReason("reason");
        publisher.publishTradeEvent(event);
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafka).send(eq("trade-events"), eq("ACC-001"), captor.capture());
        EventEnvelope envelope = captor.getValue();
        assertEquals(expectedType, envelope.eventType());
        assertNotNull(envelope.eventId());
        assertNotNull(envelope.timestamp());
        assertEquals(1, envelope.version());
        assertEquals(event.getTradeId(), envelope.payload().get("tradeId"));
        assertEquals("ACC-001", envelope.payload().get("accountId"));
        assertEquals("AAPL", envelope.payload().get("symbol"));
        assertEquals("BUY", envelope.payload().get("side"));
        assertEquals(event.getPrice(), envelope.payload().get("price"));
        assertEquals(3L, envelope.payload().get("quantity"));
        assertEquals(status, envelope.payload().get("status"));
        assertEquals("IOC", envelope.payload().get("timeInForce"));
        assertEquals(event.getTimestamp(), envelope.payload().get("timestamp"));
        assertEquals("reason", envelope.payload().get("reason"));
    }
    @Test void wrapsKafkaErrors() {
        ReflectionTestUtils.setField(publisher, "tradeEventsTopic", "trade-events");
        RuntimeException failure = new IllegalStateException("offline");
        when(kafka.send(eq("trade-events"), eq("ACC-001"), any(EventEnvelope.class))).thenThrow(failure);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> publisher.publishTradeEvent(event("INITIATED")));
        assertEquals("Failed to publish trade event", exception.getMessage());
        assertSame(failure, exception.getCause());
    }
}
