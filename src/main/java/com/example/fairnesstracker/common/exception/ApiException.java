package com.example.fairnesstracker.common.exception;

import org.springframework.http.HttpHeaders;

import java.util.List;

/**
 * An error the API reports on purpose. The {@link ErrorCode} decides the HTTP status; the message becomes
 * the response's {@code detail}, so it must be safe and useful to show to the caller.
 */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<ApiError.FieldError> errors;

    protected ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of());
    }

    protected ApiException(ErrorCode code, String detail, List<ApiError.FieldError> errors) {
        super(detail);
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<ApiError.FieldError> getErrors() {
        return errors;
    }

    /** Extra response headers, e.g. Retry-After. */
    public HttpHeaders getHeaders() {
        return HttpHeaders.EMPTY;
    }
}
