package com.neueda.leap.messaging;

import com.neueda.leap.models.OrderOutboxEvent;
import com.neueda.leap.repositories.OrderOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderOutboxRelayTest {
    final OrderOutboxRepository repository = mock(OrderOutboxRepository.class);
    @SuppressWarnings("unchecked") final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    final OrderOutboxRelay relay = new OrderOutboxRelay(repository, kafka);
    OrderOutboxEvent event(String json) {
        return new OrderOutboxEvent(UUID.randomUUID(), "order-lifecycle", "order-1", json);
    }
    @Test void onlyMarksEventsAfterKafkaAcknowledgesThem() {
        var event = event("first");
        when(repository.pending(any())).thenReturn(List.of(event));
        when(kafka.send("order-lifecycle", "order-1", "first")).thenAnswer(call -> {
            assertNull(event.getPublishedAt());
            return CompletableFuture.completedFuture(null);
        });
        relay.publishPending();
        assertNotNull(event.getPublishedAt());
    }
    @Test void retriesTheSameJsonAndDoesNotOvertakeAFailedEvent() {
        var first = event("first"); var second = event("second");
        when(repository.pending(any())).thenReturn(List.of(first, second));
        when(kafka.send("order-lifecycle", "order-1", "first"))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(kafka.send("order-lifecycle", "order-1", "second")).thenReturn(CompletableFuture.completedFuture(null));
        relay.publishPending();
        assertNull(first.getPublishedAt()); assertNull(second.getPublishedAt());
        verify(kafka, never()).send("order-lifecycle", "order-1", "second");
        relay.publishPending();
        verify(kafka, times(2)).send("order-lifecycle", "order-1", "first");
        assertNotNull(second.getPublishedAt());
    }
}
