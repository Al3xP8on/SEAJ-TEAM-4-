package com.neueda.leap.exceptions;

public class InvalidSymbolPriceException extends Exception {
    public InvalidSymbolPriceException(String message){
        super(message);
    }

    public InvalidSymbolPriceException(String message, Throwable cause){
        super(message, cause);
    }
    
}
