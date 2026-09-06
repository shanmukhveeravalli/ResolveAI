package com.resolveai.common.exception;

/**
 * Thrown when attempting to create a resource that already exists or violates a uniqueness constraint.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resourceName, String fieldName, Object value) {
        super(String.format("%s already exists with %s: '%s'", resourceName, fieldName, value));
    }
}
