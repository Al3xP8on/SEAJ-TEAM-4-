package com.neueda.leap.compliance;

import com.neueda.leap.exceptions.ComplianceException;
import com.neueda.leap.utils.LogMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pre-trade compliance: runs every rule on an order, records the results, and stops the order
 * when a rule blocks it or a warning hasn't been acknowledged.
 */
@Service
public class ComplianceService {
    private static final Logger logger = LoggerFactory.getLogger(ComplianceService.class);
    private final List<ComplianceRule> rules;
    private final ComplianceAuditRecorder audit;

    /** Spring supplies every rule, sorted by its @Order */
    public ComplianceService(List<ComplianceRule> rules, ComplianceAuditRecorder audit) {
        this.rules = List.copyOf(rules);
        this.audit = audit;
    }

    /** Runs every rule without recording anything */
    public ComplianceReport evaluate(OrderContext order) {
        return new ComplianceReport(UUID.randomUUID(), rules.stream().map(rule -> run(rule, order)).toList());
    }

    /**
     * Runs every rule, records the results, and throws when the order may not go ahead.
     *
     * @param acknowledged ids of the warning rules the customer confirmed they understand
     * @return the report, so the accepted order can be linked to its checks
     */
    public ComplianceReport enforce(OrderContext order, Collection<String> acknowledged) {
        Set<String> confirmed = acknowledged == null ? Set.of() : Set.copyOf(acknowledged);
        ComplianceReport report = evaluate(order);
        audit.record(report, order, confirmed);

        if (!report.blocks().isEmpty()) {
            logger.warn("Compliance blocked order account={} symbol={} side={} rules={}",
                    LogMaskingUtil.maskAccountId(order.account().getAccountId()), order.symbol(), order.side(),
                    report.blocks().stream().map(RuleResult::ruleId).toList());
            throw ComplianceException.blocked(report);
        }
        List<RuleResult> pending = report.unacknowledged(confirmed);
        if (!pending.isEmpty()) {
            logger.info("Compliance warning needs acknowledgement account={} symbol={} rules={}",
                    LogMaskingUtil.maskAccountId(order.account().getAccountId()), order.symbol(),
                    pending.stream().map(RuleResult::ruleId).toList());
            throw ComplianceException.needsAcknowledgement(report, pending);
        }
        if (!report.warnings().isEmpty()) {
            logger.info("Compliance warnings acknowledged account={} symbol={} rules={}",
                    LogMaskingUtil.maskAccountId(order.account().getAccountId()), order.symbol(),
                    report.warnings().stream().map(RuleResult::ruleId).toList());
        }
        return report;
    }

    /** Links the submission's audit rows to the order it created */
    public void linkOrder(ComplianceReport report, UUID orderId) {
        audit.linkOrder(report.checkGroupId(), orderId);
    }

    /** A rule that fails to run blocks the order: no trade goes ahead unchecked */
    private RuleResult run(ComplianceRule rule, OrderContext order) {
        try {
            return rule.check(order);
        } catch (RuntimeException e) {
            logger.error("Compliance rule {} failed for symbol {}", rule.id(), order.symbol(), e);
            return RuleResult.block(rule.id(), "We couldn't complete our pre-trade checks. Please try again shortly.",
                    Map.of());
        }
    }
}
