package com.neueda.exceptions;

public class InstrumentNotFoundException extends Exception {
    public InstrumentNotFoundException(String message){
        super(message);
    }

    public InstrumentNotFoundException(String message, Throwable cause){
        super(message, cause);
    }
}