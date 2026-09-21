package com.neueda.leap.exceptions;

public class InsufficientHoldingsException extends Exception {
    public InsufficientHoldingsException(String message){
        super(message);
    }

    public InsufficientHoldingsException(String message, Throwable cause){
        super(message, cause);
    }
}
