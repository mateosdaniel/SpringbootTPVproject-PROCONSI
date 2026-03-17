package com.proconsi.electrobazar.exception;

/**
 * Exception thrown when a requested resource (product, worker, sale, etc.)
 * cannot be found in the database.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}