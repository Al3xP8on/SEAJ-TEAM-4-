package com.neueda.leap.security;

/**
 * Exception thrown when a user attempts to access or modify a resource
 * that they do not have permission to access.
 */
public class AuthorizationException extends RuntimeException {
    
    public AuthorizationException(String message) {
        super(message);
    }
    
    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
