package com.neueda.leap.compliance;

/**
 * A pre-trade compliance rule (strategy pattern). Every rule runs on every order before it is
 * accepted; adding a rule means adding a Spring component that implements this interface.
 */
public interface ComplianceRule {

    /** Stable identifier stored in the audit trail and used to acknowledge warnings, e.g. RESTRICTED_LIST. */
    String id();

    /** Decides PASS, WARN or BLOCK. Must not change any state. */
    RuleResult check(OrderContext order);
}
