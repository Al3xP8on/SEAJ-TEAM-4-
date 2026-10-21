package com.neueda.leap.compliance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.models.ComplianceCheck;
import com.neueda.leap.observability.ComplianceTelemetry;
import com.neueda.leap.repositories.ComplianceCheckRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Writes the audit trail: a row per rule, a COMPLIANCE_CHECKED Kafka event (via the outbox) and the
 * metrics. Recording runs in its own transaction so a blocked order, whose transaction rolls back,
 * still leaves all three behind.
 */
@Component
public class ComplianceAuditRecorder {
    private final ComplianceCheckRepository checks;
    private final ObjectMapper mapper;
    private final ComplianceAuditPublisher publisher;

    public ComplianceAuditRecorder(ComplianceCheckRepository checks, ObjectMapper mapper, ComplianceAuditPublisher publisher) {
        this.checks = checks;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ComplianceReport report, OrderContext order, Collection<String> acknowledged) {
        Instant checkedAt = Instant.now();
        LocalDateTime now = LocalDateTime.now();
        List<ComplianceCheck> rows = report.results().stream().map(result -> new ComplianceCheck(
                report.checkGroupId(), order.account().getAccountId(), order.symbol(), order.side(),
                order.quantity(), order.price(), result.ruleId(), result.outcome(), result.reason(),
                json(result), result.outcome() == ComplianceOutcome.WARN && acknowledged.contains(result.ruleId()), now
        )).toList();
        checks.saveAll(rows);

        ComplianceDecision decision = report.decision(acknowledged);
        publisher.publish(report, order, acknowledged, decision, checkedAt);
        ComplianceTelemetry.checked(decision.name(), report.results().stream()
                .map(r -> new ComplianceTelemetry.RuleOutcome(r.ruleId(), r.outcome().name())).toList());
    }

    /** Runs in the order's transaction, so the link disappears if the order does */
    @Transactional(propagation = Propagation.MANDATORY)
    public void linkOrder(UUID checkGroupId, UUID orderId) {
        checks.linkOrder(checkGroupId, orderId);
    }

    private String json(RuleResult result) {
        if (result.details().isEmpty()) return null;
        try {
            return mapper.writeValueAsString(result.details());
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
