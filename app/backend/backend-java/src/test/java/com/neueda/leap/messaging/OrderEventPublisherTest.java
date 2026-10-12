package com.neueda.leap.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.OrderSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class OrderEventPublisherTest {
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> orders = mock(KafkaTemplate.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, EventEnvelope> trades = mock(KafkaTemplate.class);
    private final ObjectMapper mapper = mock(ObjectMapper.class);
    private final OrderEventPublisher publisher = new OrderEventPublisher(orders, trades, mapper, "orders", "trades");
    private final OrderEvent event = new OrderEvent(UUID.randomUUID(), "ACC-001", "AAPL", OrderSide.BUY, 3, new BigDecimal("10.25"), Instant.parse("2026-01-01T00:00:00Z"));
    @Test void publishesSerializedOrderAndAuditEnvelopeWithTheirPartitionKeys() throws Exception {
        when(mapper.writeValueAsString(event)).thenReturn("serialized-order");
        when(orders.send("orders", "ACC-001", "serialized-order")).thenReturn(new CompletableFuture<>());
        when(trades.send(eq("trades"), eq(event.orderId().toString()), any(EventEnvelope.class))).thenReturn(new CompletableFuture<>());
        publisher.publish(event);
        verify(orders).send("orders", "ACC-001", "serialized-order");
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(trades).send(eq("trades"), eq(event.orderId().toString()), captor.capture());
        EventEnvelope envelope = captor.getValue();
        assertEquals(EventType.ORDER_PLACED, envelope.eventType());
        assertNotNull(envelope.eventId());
        assertNotNull(envelope.timestamp());
        assertEquals(1, envelope.version());
        assertEquals(event.orderId().toString(), envelope.payload().get("orderId"));
        assertEquals("ACC-001", envelope.payload().get("accountId"));
        assertEquals("AAPL", envelope.payload().get("symbol"));
        assertEquals(OrderSide.BUY, envelope.payload().get("side"));
        assertEquals(3, envelope.payload().get("quantity"));
        assertEquals("10.25", envelope.payload().get("price"));
        assertEquals(event.createdOn().toString(), envelope.payload().get("createdOn"));
    }
    @Test void serializationFailurePreventsBothSends() throws Exception {
        JsonProcessingException failure = new JsonProcessingException("invalid") {};
        when(mapper.writeValueAsString(event)).thenThrow(failure);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> publisher.publish(event));
        assertTrue(exception.getMessage().contains(event.orderId().toString()));
        assertSame(failure, exception.getCause());
        verifyNoInteractions(orders, trades);
    }
    @Test void asynchronousSendFailuresDoNotEscapeToCaller() throws Exception {
        when(mapper.writeValueAsString(event)).thenReturn("json");
        when(orders.send("orders", "ACC-001", "json")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        when(trades.send(eq("trades"), anyString(), any(EventEnvelope.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        assertDoesNotThrow(() -> publisher.publish(event));
        verify(trades).send(eq("trades"), eq(event.orderId().toString()), any(EventEnvelope.class));
    }
    @Test void containsSynchronousAuditPublicationFailure() throws Exception {
        when(mapper.writeValueAsString(event)).thenReturn("json");
        when(orders.send("orders", "ACC-001", "json")).thenReturn(new CompletableFuture<>());
        when(trades.send(eq("trades"), anyString(), any(EventEnvelope.class))).thenThrow(new IllegalStateException("offline"));
        assertDoesNotThrow(() -> publisher.publish(event));
        verify(orders).send("orders", "ACC-001", "json");
    }
}
