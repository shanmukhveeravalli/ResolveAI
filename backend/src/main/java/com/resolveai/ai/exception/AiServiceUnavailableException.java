package com.resolveai.ai.exception;

/**
 * Exception thrown when the AI provider or subsystem is disabled, unreachable, timed out, or returning invalid payloads.
 * Normalizes to HTTP 503 Service Unavailable to ensure failure isolation from core incident management.
 */
public class AiServiceUnavailableException extends RuntimeException {

    public AiServiceUnavailableException(String message) {
        super(message);
    }

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
