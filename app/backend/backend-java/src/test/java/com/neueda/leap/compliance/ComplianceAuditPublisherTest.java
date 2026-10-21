package com.neueda.leap.compliance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.OrderOutboxEvent;
import com.neueda.leap.repositories.OrderOutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The COMPLIANCE_CHECKED event: one per submission, with the decision and every rule's result */
class ComplianceAuditPublisherTest {
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    final OrderOutboxRepository outbox = mock(OrderOutboxRepository.class);
    final ComplianceAuditPublisher publisher = new ComplianceAuditPublisher(outbox, mapper, "compliance-audit");
    final OrderContext order = new OrderContext(
            new Account("ACC-0002", "Brian", "b@example.com", null, new BigDecimal("10000.00"), AccountStatus.ACTIVE),
            new Instrument("NVDA", "NVIDIA", "EQUITY", "USD", true), OrderSide.BUY, 30, new BigDecimal("100.00"),
            0, List.of(), List.of(), symbol -> Optional.empty());
    final ComplianceReport report = new ComplianceReport(UUID.randomUUID(), List.of(
            RuleResult.pass("RESTRICTED_LIST"),
            RuleResult.warn("SINGLE_STOCK_LIMIT", "NVDA would be 30% of your portfolio.", Map.of())));

    JsonNode published() throws Exception {
        var event = ArgumentCaptor.forClass(OrderOutboxEvent.class);
        verify(outbox).save(event.capture());
        assertEquals("compliance-audit", event.getValue().getTopic());
        assertEquals("ACC-0002", event.getValue().getMessageKey(), "keyed by account, like trade events");
        return mapper.readTree(event.getValue().getPayload());
    }

    @Test void acknowledgedSubmissionIsPublishedAsAllowed() throws Exception {
        Set<String> acknowledged = Set.of("SINGLE_STOCK_LIMIT");
        publisher.publish(report, order, acknowledged, report.decision(acknowledged), Instant.parse("2026-10-19T12:00:00Z"));

        JsonNode event = published();
        assertEquals("COMPLIANCE_CHECKED", event.get("eventType").asText());
        assertEquals("2026-10-19T12:00:00Z", event.get("timestamp").asText());
        JsonNode payload = event.get("payload");
        assertEquals(report.checkGroupId().toString(), payload.get("checkGroupId").asText());
        assertEquals("NVDA", payload.get("symbol").asText());
        assertEquals("ALLOWED", payload.get("decision").asText());
        assertEquals(2, payload.get("results").size());
        JsonNode warning = payload.get("results").get(1);
        assertEquals("SINGLE_STOCK_LIMIT", warning.get("rule").asText());
        assertEquals("WARN", warning.get("outcome").asText());
        assertTrue(warning.get("acknowledged").asBoolean());
        assertFalse(payload.get("results").get(0).get("acknowledged").asBoolean());
    }

    @Test void decisionReflectsBlocksAndUnacknowledgedWarnings() {
        assertEquals(ComplianceDecision.NEEDS_ACKNOWLEDGEMENT, report.decision(Set.of()));
        assertEquals(ComplianceDecision.ALLOWED, report.decision(Set.of("SINGLE_STOCK_LIMIT")));
        var blocked = new ComplianceReport(UUID.randomUUID(), List.of(
                RuleResult.block("RESTRICTED_LIST", "Restricted", Map.of()),
                RuleResult.warn("SINGLE_STOCK_LIMIT", "Concentrated", Map.of())));
        assertEquals(ComplianceDecision.BLOCKED, blocked.decision(Set.of("SINGLE_STOCK_LIMIT", "RESTRICTED_LIST")));
    }
}
