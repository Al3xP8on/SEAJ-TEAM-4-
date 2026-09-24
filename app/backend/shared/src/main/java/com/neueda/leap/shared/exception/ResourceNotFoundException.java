package com.neueda.leap.shared.exception;

/**
 * Exception thrown when a requested resource is not found
 */
public class ResourceNotFoundException extends SeajException {

    public ResourceNotFoundException(String resourceName, Long id) {
        super(
            String.format("%s with id %d not found", resourceName, id),
            "RESOURCE_NOT_FOUND",
            404
        );
    }

    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND", 404);
    }
}
