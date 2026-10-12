package com.neueda.leap.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class MarketDataProducerTest {
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, EventEnvelope> kafka = mock(KafkaTemplate.class);
    private final MarketDataProducer producer = new MarketDataProducer(kafka);
    private EventEnvelope event(Map<String, Object> payload) {
        return new EventEnvelope(UUID.randomUUID(), EventType.PRICE_UPDATE, Instant.now(), 1, payload);
    }
    @Test void publishesUsingSymbolAsKey() {
        EventEnvelope event = event(Map.of("symbol", "AAPL"));
        producer.publishMarketData(event);
        verify(kafka).send("market-data", "AAPL", event);
    }
    @Test void generatesUuidKeyWhenSymbolMissing() {
        EventEnvelope event = event(Map.of());
        producer.publishMarketData(event);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("market-data"), key.capture(), same(event));
        assertDoesNotThrow(() -> UUID.fromString(key.getValue()));
    }
    @Test void rejectsNullEnvelope() {
        assertThrows(IllegalArgumentException.class, () -> producer.publishMarketData(null));
        verifyNoInteractions(kafka);
    }
    @Test void wrapsKafkaFailureWithOriginalCause() {
        EventEnvelope event = event(Map.of("symbol", "AAPL"));
        RuntimeException failure = new IllegalStateException("offline");
        when(kafka.send("market-data", "AAPL", event)).thenThrow(failure);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> producer.publishMarketData(event));
        assertEquals("Failed to publish market data event", exception.getMessage());
        assertSame(failure, exception.getCause());
    }
}
