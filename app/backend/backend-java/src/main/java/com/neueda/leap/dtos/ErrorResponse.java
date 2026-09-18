package com.neueda.leap.dtos;

public class ErrorResponse {
    private String message;
    private Throwable cause;

    public ErrorResponse(String message, Throwable cause){
        this.message = message;
        this.cause = cause;
    }

    public String getMessage(){
        return this.message;
    }

    public Throwable getCause(){
        return this.cause;
    }
}
