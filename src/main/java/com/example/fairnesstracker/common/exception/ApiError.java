package com.example.fairnesstracker.common.exception;

import com.example.fairnesstracker.common.web.RequestIdFilter;
import org.slf4j.MDC;

import java.time.Instant;

// requestId lets a caller quote the exact failing request when reporting a problem
public record ApiError(
        int status,
        String error,
        String message,
        Instant timestamp,
        String requestId
) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Instant.now(), MDC.get(RequestIdFilter.MDC_KEY));
    }
}
