package com.neueda.leap.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.enums.*;
import com.neueda.leap.models.*;
import com.neueda.leap.repositories.OrderOutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderEventOutboxTest {
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    final OrderOutboxRepository repository = mock(OrderOutboxRepository.class);
    final OrderEventOutbox outbox = new OrderEventOutbox(repository, mapper, "order-lifecycle", "orders");
    Order order() {
        var account = new Account("ACC-0001", "Jay", "jay@example.com", null, new BigDecimal("1000"), AccountStatus.ACTIVE);
        return new Order(account, new Instrument("AAPL", "Apple", "EQUITY", "USD", true), 2,
            new BigDecimal("100.00"), OrderSide.BUY, "key");
    }
    @Test void persistsStableEventIdentityReasonAndOrderKeyBeforeDelivery() throws Exception {
        Order order = order(); order.setStatus(OrderStatus.REJECTED);
        outbox.lifecycle(order, EventType.ORDER_REJECTED, "Insufficient cash");
        var capture = ArgumentCaptor.forClass(OrderOutboxEvent.class);
        verify(repository).save(capture.capture());
        var row = capture.getValue(); var json = mapper.readTree(row.getPayload());
        assertEquals(row.getEventId().toString(), json.path("eventId").asText());
        assertEquals(order.getId().toString(), row.getMessageKey());
        assertEquals("Insufficient cash", json.path("payload").path("reason").asText());
        assertEquals("REJECTED", json.path("payload").path("status").asText());
    }
    @Test void commandsRetainOriginalLimitTypeAndCancellationAction() throws Exception {
        Order order = order(); order.setOrderType(OrderType.MARKET);
        outbox.command(order, "CANCEL");
        var capture = ArgumentCaptor.forClass(OrderOutboxEvent.class);
        verify(repository).save(capture.capture());
        var row = capture.getValue(); var json = mapper.readTree(row.getPayload());
        assertEquals("orders", row.getTopic());
        assertEquals("ACC-0001", row.getMessageKey());
        assertEquals("MARKET", json.path("orderType").asText());
        assertEquals("CANCEL", json.path("action").asText());
        assertEquals(new BigDecimal("100.00"), json.path("price").decimalValue().setScale(2));
    }
}
