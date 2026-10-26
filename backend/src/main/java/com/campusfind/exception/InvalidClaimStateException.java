package com.campusfind.exception;

/**
 * Exception thrown when a claim operation violates report/claim lifecycle constraints.
 */
public class InvalidClaimStateException extends RuntimeException {

    public InvalidClaimStateException(String message) {
        super(message);
    }
}
