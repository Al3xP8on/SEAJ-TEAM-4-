package com.neueda.leap.enums;

/**
 * PositionStatus enumeration representing the status of a trading position.
 */
public enum PositionStatus {
    OPEN("OPEN", "Position is currently open with active holdings"),
    PARTIALLY_CLOSED("PARTIALLY_CLOSED", "Position has been partially closed"),
    CLOSED("CLOSED", "Position has been fully closed"),
    LIQUIDATED("LIQUIDATED", "Position was liquidated");

    private final String code;
    private final String description;

    PositionStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
