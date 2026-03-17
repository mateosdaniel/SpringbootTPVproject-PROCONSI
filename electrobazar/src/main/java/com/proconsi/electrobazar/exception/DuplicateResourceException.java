package com.proconsi.electrobazar.exception;

/**
 * Exception thrown when an attempt is made to create or update a resource
 * that would result in a duplicate (e.g., duplicate product name or worker username).
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}