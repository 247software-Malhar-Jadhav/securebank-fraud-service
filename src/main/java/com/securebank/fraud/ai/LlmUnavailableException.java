package com.securebank.fraud.ai;

/**
 * Signals that the live LLM could not produce an answer (circuit open, timeout, HTTP error,
 * or unexpected response). It is caught by the orchestrating services, which then fall back
 * to {@link DeterministicAiProvider}. This is the seam that makes graceful degradation work.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
