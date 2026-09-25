package com.neueda.leap.exceptions;

import com.neueda.leap.enums.OrderErrorCode;

public class OrderException extends RuntimeException {
    
    private final OrderErrorCode errorCode;
    private final String context; 

    public OrderException(String message) {
        super(message);
        this.errorCode = null;
        this.context = null;
    }

    public OrderException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.context = null;
    }

    public OrderException(String message, OrderErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.context = null;
    }

    public OrderException(String message, OrderErrorCode errorCode, String context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context;
    }

    public OrderErrorCode getErrorCode() {
        return errorCode;
    }

    public String getContext() {
        return context;
    }
}