package com.resolveai.auth.exception;

/**
 * Thrown when a JWT or refresh token is invalid, expired, or fails signature verification.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
