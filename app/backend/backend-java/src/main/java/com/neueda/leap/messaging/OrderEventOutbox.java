package com.neueda.leap.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.OrderOutboxEvent;
import com.neueda.leap.repositories.OrderOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/** The caller's order transaction owns both the state change and its Kafka messages. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class OrderEventOutbox {
    private final OrderOutboxRepository repository;
    private final ObjectMapper mapper;
    private final String lifecycleTopic;
    private final String ordersTopic;

    public OrderEventOutbox(OrderOutboxRepository repository, ObjectMapper mapper,
            @Value("${trading.kafka.topics.order-lifecycle:order-lifecycle}") String lifecycleTopic,
            @Value("${trading.kafka.topics.orders:orders}") String ordersTopic) {
        this.repository = repository;
        this.mapper = mapper;
        this.lifecycleTopic = lifecycleTopic;
        this.ordersTopic = ordersTopic;
    }

    public void lifecycle(Order order, EventType type, String reason) {
        lifecycle(order, type, reason, Map.of());
    }

    public void lifecycle(Order order, EventType type, String reason, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("accountId", order.getAccount().getAccountId());
        payload.put("symbol", order.getInstrument().getSymbol());
        payload.put("side", order.getSide());
        payload.put("quantity", order.getQuantity());
        payload.put("orderType", order.getOrderType());
        payload.put("price", order.getPrice());
        payload.put("status", order.getStatus());
        payload.put("reason", reason);
        payload.putAll(details);
        UUID id = UUID.randomUUID();
        enqueue(id, lifecycleTopic, order.getId().toString(),
                new EventEnvelope(id, type, Instant.now(), 1, payload));
    }

    public void command(Order order, String action) {
        UUID id = UUID.randomUUID();
        OrderEvent command = new OrderEvent(order.getId(), order.getAccount().getAccountId(),
                order.getInstrument().getSymbol(), order.getSide(), Math.toIntExact(order.getQuantity()),
                order.getPrice(), order.getCreatedAt().toInstant(ZoneOffset.UTC), order.getOrderType(), action);
        enqueue(id, ordersTopic, order.getAccount().getAccountId(), command);
    }

    private void enqueue(UUID id, String topic, String key, Object value) {
        try {
            repository.save(new OrderOutboxEvent(id, topic, key, mapper.writeValueAsString(value)));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize order event", error);
        }
    }
}
