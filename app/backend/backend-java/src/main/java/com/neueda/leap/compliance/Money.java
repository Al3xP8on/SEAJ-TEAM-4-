package com.neueda.leap.compliance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formatting for amounts and shares in reasons shown to customers */
public final class Money {
    private Money() {
    }

    public static String usd(BigDecimal amount) {
        return String.format("$%,.2f", amount);
    }

    public static String percent(BigDecimal share) {
        return share.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
