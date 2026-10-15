package com.neueda.leap.messaging;

public class TradeEventPublishException extends RuntimeException {
    public TradeEventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}