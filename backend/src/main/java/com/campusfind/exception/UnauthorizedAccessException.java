package com.campusfind.exception;

/**
 * Exception thrown when a user attempts to access or modify a resource they do not own or have permission for.
 */
public class UnauthorizedAccessException extends RuntimeException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
