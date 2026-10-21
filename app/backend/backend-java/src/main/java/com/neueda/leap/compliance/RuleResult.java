package com.neueda.leap.compliance;

import java.util.Map;

/**
 * One rule's decision about one order. The reason is shown to the customer as written,
 * so it is plain English and never includes another customer's data.
 */
public record RuleResult(String ruleId, ComplianceOutcome outcome, String reason, Map<String, Object> details) {

    public RuleResult {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static RuleResult pass(String ruleId) {
        return new RuleResult(ruleId, ComplianceOutcome.PASS, null, Map.of());
    }

    public static RuleResult warn(String ruleId, String reason, Map<String, Object> details) {
        return new RuleResult(ruleId, ComplianceOutcome.WARN, reason, details);
    }

    public static RuleResult block(String ruleId, String reason, Map<String, Object> details) {
        return new RuleResult(ruleId, ComplianceOutcome.BLOCK, reason, details);
    }
}
