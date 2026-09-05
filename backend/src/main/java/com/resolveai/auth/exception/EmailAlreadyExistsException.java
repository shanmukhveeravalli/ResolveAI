package com.resolveai.auth.exception;

/**
 * Thrown when attempting to register an account with an existing email address.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
