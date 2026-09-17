package com.neueda.leap.exceptions;

/**
 * Custom exception for order-related errors.
 * specific exception for domain-specific errors.
 */
public class OrderException extends RuntimeException {
    
    private final String orderCode;
    private final String errorCode;

    public OrderException(String message) {
        super(message);
        this.orderCode = null;
        this.errorCode = null;
    }

    public OrderException(String message, Throwable cause) {
        super(message, cause);
        this.orderCode = null;
        this.errorCode = null;
    }

    public OrderException(String message, String orderCode, String errorCode) {
        super(message);
        this.orderCode = orderCode;
        this.errorCode = errorCode;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}