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
    /** No access token, or it's invalid or expired: refresh it, or sign in again. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    /** Wrong email or password. Doesn't say which. */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    /** Signed in, but this role or this user may not do that or see that data. */
    FORBIDDEN(HttpStatus.FORBIDDEN),
    /** The password must be changed first (POST /api/v1/auth/change-password). */
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN),
    /** An admin disabled this account. */
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    /** Too many failed sign-ins; locked for a while (see Retry-After). */
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    /** Too many requests from this client; retry after the Retry-After header's seconds. */
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    /** The refresh token is missing, unknown, expired or revoked: sign in again. */
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    /** An already-used refresh token was presented, so every session from that sign-in was ended. */
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED),
    /** The resource or route doesn't exist. */
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    /** The request is valid but clashes with existing data, e.g. a duplicate name or a delete of something in use. */
    CONFLICT(HttpStatus.CONFLICT),
    /** The same kind of PagerDuty sync is already running; retry when it finishes (see GET .../sync-status). */
    SYNC_IN_PROGRESS(HttpStatus.CONFLICT),
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
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
        };
    }
}
