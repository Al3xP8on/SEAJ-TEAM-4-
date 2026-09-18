package com.neueda.leap.exceptions;

public class InvalidSymbolException extends RuntimeException {
    public InvalidSymbolException(String message){
        super(message);
    }

    public InvalidSymbolException(String message, Throwable cause){
        super(message, cause);
    }
}
