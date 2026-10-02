package com.neueda.leap.services;

import java.math.BigDecimal;

/**
 * Pure function for pricing validation.
 * Takes order and market data as input, returns a pricing decision with no side effects.
 * No database access, no socket calls, no external dependencies.
 */
public record PricingDecision(
    boolean canExecute,
    String reason,
    BigDecimal executionPrice
) {
    private static final String BUY_SIDE = "BUY";
    private static final String SELL_SIDE = "SELL";

    /**
     * Validates if an order can be executed based on current market conditions.
     * Pure function: order + quote in, decision out.
     *
     * @param orderPrice the order's limit price
     * @param orderSide "BUY" or "SELL"
     * @param quotePrice the current market quote price
     * @param tradeable whether the instrument is tradeable
     * @return PricingDecision containing execution decision and reason
     */
    public static PricingDecision decide(
            BigDecimal orderPrice,
            String orderSide,
            BigDecimal quotePrice,
            boolean tradeable) {

        // Validate instrument is tradeable
        if (!tradeable) {
            return new PricingDecision(false, "Instrument not tradeable", null);
        }

        // Validate inputs are not null
        if (orderPrice == null || quotePrice == null) {
            return new PricingDecision(false, "Missing price data", null);
        }

        if (BUY_SIDE.equalsIgnoreCase(orderSide)) {
            return validateBuyOrder(orderPrice, quotePrice);
        }

        if (SELL_SIDE.equalsIgnoreCase(orderSide)) {
            return validateSellOrder(orderPrice, quotePrice);
        }

        return new PricingDecision(false, "Unknown order side: " + orderSide, null);
    }

    private static PricingDecision validateBuyOrder(BigDecimal orderPrice, BigDecimal quotePrice) {
        if (quotePrice.compareTo(orderPrice) <= 0) {
            return new PricingDecision(true, "BUY execution at quote", quotePrice);
        }
        return new PricingDecision(
            false,
            "BUY quote exceeds order limit: quote=" + quotePrice + ", limit=" + orderPrice,
            null
        );
    }

    private static PricingDecision validateSellOrder(BigDecimal orderPrice, BigDecimal quotePrice) {
        if (quotePrice.compareTo(orderPrice) >= 0) {
            return new PricingDecision(true, "SELL execution at quote", quotePrice);
        }
        return new PricingDecision(
            false,
            "SELL quote below order limit: quote=" + quotePrice + ", limit=" + orderPrice,
            null
        );
    }
}
