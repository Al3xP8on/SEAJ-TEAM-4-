package com.neueda.leap.exceptions;

import com.neueda.leap.compliance.ComplianceReport;
import com.neueda.leap.compliance.RuleResult;
import com.neueda.leap.enums.OrderErrorCode;

import java.util.List;

/**
 * An order stopped by pre-trade compliance: blocked outright, or held until the customer
 * acknowledges its warnings. Extends OrderException so existing order handling still applies;
 * the API answers 422 with every rule's reason.
 */
public class ComplianceException extends OrderException {
    private final transient ComplianceReport report;
    private final transient List<RuleResult> reasons;

    private ComplianceException(String message, OrderErrorCode code, ComplianceReport report, List<RuleResult> reasons) {
        super(message, code);
        this.report = report;
        this.reasons = List.copyOf(reasons);
    }

    public static ComplianceException blocked(ComplianceReport report) {
        List<RuleResult> blocks = report.blocks();
        return new ComplianceException(blocks.get(0).reason(), OrderErrorCode.COMPLIANCE_BLOCKED, report, blocks);
    }

    public static ComplianceException needsAcknowledgement(ComplianceReport report, List<RuleResult> warnings) {
        String reasons = String.join(" ", warnings.stream().map(RuleResult::reason).toList());
        return new ComplianceException(reasons + " Confirm to place the order anyway.",
                OrderErrorCode.COMPLIANCE_WARNING, report, warnings);
    }

    public ComplianceReport getReport() { return report; }

    /** The results that stopped the order: the blocks, or the unacknowledged warnings */
    public List<RuleResult> getReasons() { return reasons; }
}
