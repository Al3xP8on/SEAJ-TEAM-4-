package com.neueda.leap.compliance;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Every rule's result for one order submission. The group id ties the audit rows together. */
public record ComplianceReport(UUID checkGroupId, List<RuleResult> results) {

    public ComplianceReport {
        results = List.copyOf(results);
    }

    public List<RuleResult> blocks() {
        return results.stream().filter(r -> r.outcome() == ComplianceOutcome.BLOCK).toList();
    }

    public List<RuleResult> warnings() {
        return results.stream().filter(r -> r.outcome() == ComplianceOutcome.WARN).toList();
    }

    /** What happened to the order: blocked, held for acknowledgement, or allowed through */
    public ComplianceDecision decision(Collection<String> acknowledgedRuleIds) {
        if (!blocks().isEmpty()) return ComplianceDecision.BLOCKED;
        if (!unacknowledged(acknowledgedRuleIds).isEmpty()) return ComplianceDecision.NEEDS_ACKNOWLEDGEMENT;
        return ComplianceDecision.ALLOWED;
    }

    /** Warnings the customer has not confirmed they understand */
    public List<RuleResult> unacknowledged(Collection<String> acknowledgedRuleIds) {
        return warnings().stream().filter(r -> !acknowledgedRuleIds.contains(r.ruleId())).toList();
    }
}
