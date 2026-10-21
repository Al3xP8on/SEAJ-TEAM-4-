package com.neueda.leap.enums;

import java.math.BigDecimal;

/**
 * How much risk an account has agreed to take. Sets the limits the compliance rules apply.
 * Accounts without a profile are treated as BALANCED.
 */
public enum RiskProfile {
    // single-stock warn share, single-stock block share, largest single order (null = no limit)
    CONSERVATIVE(new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("5000")),
    BALANCED(new BigDecimal("0.25"), new BigDecimal("0.50"), new BigDecimal("25000")),
    AGGRESSIVE(new BigDecimal("0.40"), new BigDecimal("0.80"), null);

    private final BigDecimal concentrationWarn;
    private final BigDecimal concentrationBlock;
    private final BigDecimal maxOrderValue;

    RiskProfile(BigDecimal concentrationWarn, BigDecimal concentrationBlock, BigDecimal maxOrderValue) {
        this.concentrationWarn = concentrationWarn;
        this.concentrationBlock = concentrationBlock;
        this.maxOrderValue = maxOrderValue;
    }

    public BigDecimal concentrationWarn() { return concentrationWarn; }
    public BigDecimal concentrationBlock() { return concentrationBlock; }
    /** The largest order value allowed for this profile, or null when there is no limit. */
    public BigDecimal maxOrderValue() { return maxOrderValue; }

    /** "balanced", for messages shown to customers */
    public String label() { return name().toLowerCase(); }
}
