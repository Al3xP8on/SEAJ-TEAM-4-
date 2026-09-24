package com.neueda.leap.exceptions;

public class PositionNotFoundException extends Exception {
    public PositionNotFoundException(String message) {
        super(message);
    }

    public PositionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
