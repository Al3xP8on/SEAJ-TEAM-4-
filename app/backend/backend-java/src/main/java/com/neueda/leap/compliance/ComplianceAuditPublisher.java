package com.neueda.leap.compliance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.messaging.EventEnvelope;
import com.neueda.leap.messaging.EventType;
import com.neueda.leap.models.OrderOutboxEvent;
import com.neueda.leap.repositories.OrderOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Queues a COMPLIANCE_CHECKED Kafka event for each order submission. It goes through the outbox table
 * in the audit's own transaction, so blocked orders are published too, and only once their audit rows
 * are saved. The existing outbox relay sends it.
 */
@Component
public class ComplianceAuditPublisher {
    private final OrderOutboxRepository outbox;
    private final ObjectMapper mapper;
    private final String topic;

    public ComplianceAuditPublisher(OrderOutboxRepository outbox, ObjectMapper mapper,
            @Value("${trading.kafka.topics.compliance-audit:compliance-audit}") String topic) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.topic = topic;
    }

    public void publish(ComplianceReport report, OrderContext order, Collection<String> acknowledged,
                        ComplianceDecision decision, Instant checkedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkGroupId", report.checkGroupId());
        payload.put("accountId", order.account().getAccountId());
        payload.put("symbol", order.symbol());
        payload.put("side", order.side());
        payload.put("quantity", order.quantity());
        payload.put("price", order.price());
        payload.put("decision", decision);
        payload.put("results", report.results().stream().map(result -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rule", result.ruleId());
            entry.put("outcome", result.outcome());
            entry.put("reason", result.reason());
            entry.put("acknowledged", result.outcome() == ComplianceOutcome.WARN && acknowledged.contains(result.ruleId()));
            return entry;
        }).toList());
        UUID eventId = UUID.randomUUID();
        try {
            outbox.save(new OrderOutboxEvent(eventId, topic, order.account().getAccountId(), mapper.writeValueAsString(
                    new EventEnvelope(eventId, EventType.COMPLIANCE_CHECKED, checkedAt, 1, payload))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize compliance audit event", e);
        }
    }
}
