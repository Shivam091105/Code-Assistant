package com.example.codeassistant.common;

/**
 * Raised when a call to an external dependency (GitHub API, embedding
 * provider, LLM provider) fails. Kept distinct from internal errors so the
 * global handler can return a clearer message without leaking details.
 */
public class UpstreamServiceException extends RuntimeException {
    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public UpstreamServiceException(String message) {
        super(message);
    }
}
