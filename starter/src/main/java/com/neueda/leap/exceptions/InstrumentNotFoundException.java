package com.neueda.leap.exceptions;

public class InstrumentNotFoundException extends Exception {
    public InstrumentNotFoundException(String message){
        super(message);
    }

    public InstrumentNotFoundException(String message, Throwable cause){
        super(message, cause);
    }
}