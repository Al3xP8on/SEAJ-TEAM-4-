package com.neueda.leap.enums;

/**
 * OrderSide enumeration representing the direction of the order.
 * Follows Single Responsibility and type safety principles.
 */
public enum OrderSide {
    BUY("BUY", "Purchase order - increases holdings"),
    SELL("SELL", "Sale order - decreases holdings");

    private final String code;
    private final String description;

    OrderSide(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this is a buy order.
     */
    public boolean isBuy() {
        return this == BUY;
    }

    /**
     * Check if this is a sell order.
     */
    public boolean isSell() {
        return this == SELL;
    }

    /**
     * Get the opposite side of this order.
     */
    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
