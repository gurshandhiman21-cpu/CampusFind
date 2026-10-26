package com.campusfind.exception;

/**
 * Exception thrown when the rate limit for failed login attempts is exceeded.
 */
public class LoginRateLimitExceededException extends RuntimeException {

    public LoginRateLimitExceededException(String message) {
        super(message);
    }
}
