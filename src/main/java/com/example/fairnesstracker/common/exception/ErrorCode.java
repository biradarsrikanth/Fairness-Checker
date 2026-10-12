package com.example.fairnesstracker.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Stable, machine-readable error categories returned as {@code code} in every {@link ApiError}. Clients
 * branch on these, never on {@code detail} (which is for people and may change). Adding a code is a
 * compatible change; renaming or removing one is not.
 */
public enum ErrorCode {

    /** Input failed validation; {@code errors} names the fields. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    /** The request body is missing or isn't valid JSON. */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    /** Any other client error the API can't describe more precisely. */
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    /** The resource or route doesn't exist. */
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    /** The request is valid but clashes with existing data, e.g. a duplicate name or a delete of something in use. */
    CONFLICT(HttpStatus.CONFLICT),
    /** A service we depend on (scorer, PagerDuty) returned an error or an unexpected response. */
    UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY),
    /** A service we depend on can't be reached. */
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    /** A service we depend on didn't answer in time. */
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    /** A bug on our side; the detail is deliberately generic. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /** The code for a status raised by Spring itself (unknown route, wrong method, ...). */
    public static ErrorCode forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
        };
    }
}
