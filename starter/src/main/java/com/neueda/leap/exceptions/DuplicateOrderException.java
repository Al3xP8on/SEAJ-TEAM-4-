package com.neueda.exceptions;

public class DuplicateOrderException extends Exception {
    public DuplicateOrderException(String message){
        super(message);
    }

    public DuplicateOrderException(String message, Throwable cause){
        super(message, cause);
    }
}
