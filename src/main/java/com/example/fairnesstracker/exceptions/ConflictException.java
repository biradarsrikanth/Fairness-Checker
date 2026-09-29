package com.example.fairnesstracker.exceptions;

// The request is valid but conflicts with existing data (409), e.g. deleting a team that has engineers
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
