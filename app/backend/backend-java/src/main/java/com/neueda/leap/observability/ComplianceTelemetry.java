package com.neueda.leap.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Pre-trade compliance metrics for the Grafana dashboard, in the same style as OrderTelemetry.
 * Attributes stay low-cardinality: the decision, the rule id and its outcome; never accounts or symbols.
 */
public final class ComplianceTelemetry {
    private static final String SCOPE = "com.neueda.leap.compliance";
    private static final AttributeKey<String> DECISION = AttributeKey.stringKey("compliance.decision");
    private static final AttributeKey<String> RULE = AttributeKey.stringKey("compliance.rule");
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("compliance.outcome");

    private static volatile Instruments instruments;

    private ComplianceTelemetry() {
    }

    /** One rule's result, as rule id and outcome name */
    public record RuleOutcome(String rule, String outcome) { }

    /**
     * Counts one submission and each rule's result, once the audit transaction commits.
     *
     * @param decision BLOCKED, NEEDS_ACKNOWLEDGEMENT or ALLOWED
     */
    public static void checked(String decision, List<RuleOutcome> results) {
        Span.current().setAttribute(DECISION, decision);
        afterCommit(() -> {
            Instruments i = instruments();
            i.decisions.add(1, Attributes.of(DECISION, decision));
            for (RuleOutcome result : results) {
                i.results.add(1, Attributes.of(RULE, result.rule(), OUTCOME, result.outcome()));
            }
        });
    }

    private static void afterCommit(Runnable record) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    record.run();
                }
            });
        } else {
            record.run();
        }
    }

    private static Instruments instruments() {
        Instruments current = instruments;
        if (current == null) {
            current = new Instruments(GlobalOpenTelemetry.getMeter(SCOPE));
            instruments = current;
        }
        return current;
    }

    private static final class Instruments {
        final LongCounter decisions;
        final LongCounter results;

        Instruments(Meter meter) {
            decisions = meter.counterBuilder("seaj.compliance.decisions")
                    .setDescription("Order submissions by compliance decision: blocked, needs acknowledgement or allowed")
                    .setUnit("{submission}")
                    .build();
            results = meter.counterBuilder("seaj.compliance.rule.results")
                    .setDescription("Compliance rule results by rule and outcome")
                    .setUnit("{check}")
                    .build();
        }
    }
}
