package com.campusfind.exception;

/**
 * Exception thrown when attempting to register an email address that is already registered.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
