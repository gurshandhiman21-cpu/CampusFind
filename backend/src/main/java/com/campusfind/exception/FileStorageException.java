package com.campusfind.exception;

/**
 * Exception thrown when a file storage operation (such as directory creation,
 * writing bytes, or reading from disk) encounters an unrecoverable IO failure.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
