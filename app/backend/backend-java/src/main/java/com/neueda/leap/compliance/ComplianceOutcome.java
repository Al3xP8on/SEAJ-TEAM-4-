package com.neueda.leap.compliance;

/** What a compliance rule decided about an order. */
public enum ComplianceOutcome {
    /** Nothing to flag. */
    PASS,
    /** Allowed, but the customer must acknowledge the reason before the order is accepted. */
    WARN,
    /** Not allowed; the order is never created. */
    BLOCK
}
