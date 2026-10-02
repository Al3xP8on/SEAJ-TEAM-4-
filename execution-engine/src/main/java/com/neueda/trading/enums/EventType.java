package com.neueda.trading.enums;

public enum EventType {
    ORDER_PLACED("Event for order placement"),
    TRADE_EXECUTED("Event for executed trades"),

    ORDER_FILLED("Event for filled orders"),
    ORDER_REJECTED("Event for rejected orders"),
    ORDER_CANCELLED("Event for cancelled orders"),
    
    PRICE_UPDATE("Event for price updates");

    private final String description;

    EventType(String description){
        this.description = description;
    }

    public String getDescription(){
        return this.description;
    }
}
