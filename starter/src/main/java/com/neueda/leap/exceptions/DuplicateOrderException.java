package com.neueda.leap.exceptions;

public class DuplicateOrderException extends Exception {
    public DuplicateOrderException(String message){
        super(message);
    }

    public DuplicateOrderException(String message, Throwable cause){
        super(message, cause);
    }
}
