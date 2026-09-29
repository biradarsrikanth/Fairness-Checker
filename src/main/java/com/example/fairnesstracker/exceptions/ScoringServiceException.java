package com.example.fairnesstracker.exceptions;

/**
 * A scorer call failed. {@code status} is what the gateway returns to its caller;
 * {@code upstreamStatus} is what the scorer returned (0 when it never answered).
 */
public class ScoringServiceException extends RuntimeException {

    private final int status;
    private final int upstreamStatus;

    public ScoringServiceException(int status, int upstreamStatus, String message) {
        super(message);
        this.status = status;
        this.upstreamStatus = upstreamStatus;
    }

    public int getStatus() {
        return status;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
