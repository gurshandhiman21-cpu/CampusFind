package com.campusfind.exception;

/**
 * Exception thrown when a student attempts to file a duplicate claim on an item.
 */
public class DuplicateClaimException extends RuntimeException {

    public DuplicateClaimException(String message) {
        super(message);
    }
}
