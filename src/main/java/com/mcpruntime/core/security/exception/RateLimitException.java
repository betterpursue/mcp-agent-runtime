package com.mcpruntime.core.security.exception;

/**
 * Thrown when a tool invocation exceeds its configured rate limit.
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
