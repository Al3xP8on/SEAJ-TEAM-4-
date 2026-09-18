package com.neueda.leap.exceptions;

public class AccountNotActiveException extends Exception {
    public AccountNotActiveException(String message){
        super(message);
    }

    public AccountNotActiveException(String message, Throwable cause){
        super(message, cause);
    }
}