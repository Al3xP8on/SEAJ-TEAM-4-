package com.neueda.leap.compliance;

import com.neueda.leap.compliance.rules.*;
import com.neueda.leap.repositories.RestrictedInstrumentRepository;

import java.util.List;

/** Builds the compliance engine with every real rule, for tests that construct services by hand */
public final class ComplianceTestSupport {
    private ComplianceTestSupport() {
    }

    public static List<ComplianceRule> rules(RestrictedInstrumentRepository restricted) {
        return List.of(new RestrictedListRule(restricted), new CashAvailableRule(), new HoldingsAvailableRule(),
                new SingleStockLimitRule(), new RiskProfileRule());
    }

    public static ComplianceService service(RestrictedInstrumentRepository restricted, ComplianceAuditRecorder audit) {
        return new ComplianceService(rules(restricted), audit);
    }
}
