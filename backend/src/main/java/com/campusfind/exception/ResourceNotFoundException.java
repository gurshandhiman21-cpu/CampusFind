package com.campusfind.exception;

/**
 * Exception thrown when a requested resource (such as a Report) cannot be found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
