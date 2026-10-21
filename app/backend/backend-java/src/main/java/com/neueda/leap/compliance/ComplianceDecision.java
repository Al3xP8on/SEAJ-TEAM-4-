package com.neueda.leap.compliance;

/** The overall result of one order submission's compliance checks */
public enum ComplianceDecision {
    /** At least one rule blocked it; no order was created */
    BLOCKED,
    /** Warnings the customer hasn't acknowledged yet; no order was created */
    NEEDS_ACKNOWLEDGEMENT,
    /** Every rule passed, or every warning was acknowledged */
    ALLOWED
}
