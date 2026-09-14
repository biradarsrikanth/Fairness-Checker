package com.example.fairnesstracker.exceptions;

public class ScoringServiceException extends RuntimeException {

    private final int status;

    public ScoringServiceException(int status, String message) {
        super(message);
        this.status = status;
    }

    public ScoringServiceException(String message, Throwable cause) {
        super(message, cause);
        this.status = 503;
    }

    public int getStatus() {
        return status;
    }
}