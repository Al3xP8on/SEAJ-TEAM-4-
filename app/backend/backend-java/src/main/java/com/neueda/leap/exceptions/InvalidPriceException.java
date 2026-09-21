package com.neueda.leap.exceptions;

public class InvalidPriceException extends RuntimeException {
    public InvalidPriceException(String message){
        super(message);
    }

    public InvalidPriceException(String message, Throwable cause){
        super(message, cause);
    }
    
}
