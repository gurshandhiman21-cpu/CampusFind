package com.campusfind.exception;

/**
 * Exception thrown when an uploaded file fails validation
 * (e.g., empty file, unsupported MIME type, size limit exceeded, or corrupted content).
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }

    public InvalidFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
