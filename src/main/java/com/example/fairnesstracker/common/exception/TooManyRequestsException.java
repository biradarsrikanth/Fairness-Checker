package com.example.fairnesstracker.common.exception;

import org.springframework.http.HttpHeaders;

import java.time.Duration;

/** 429, with Retry-After in whole seconds. */
public class TooManyRequestsException extends ApiException {

    private final Duration retryAfter;

    public TooManyRequestsException(String detail, Duration retryAfter) {
        super(ErrorCode.TOO_MANY_REQUESTS, detail);
        this.retryAfter = retryAfter;
    }

    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        // Rounded up, so a client that waits exactly this long isn't refused again
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, (retryAfter.toMillis() + 999) / 1000)));
        return headers;
    }
}
