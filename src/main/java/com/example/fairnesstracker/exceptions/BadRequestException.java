package com.example.fairnesstracker.exceptions;

// Input that passes bean validation but is still wrong (400), e.g. an unknown team or timezone
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
