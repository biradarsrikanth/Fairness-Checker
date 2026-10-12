package com.example.fairnesstracker.common.exception;

/** 403: signed in, but not allowed to do this or to see this data. */
public class ForbiddenException extends ApiException {

    public static final String DEFAULT_DETAIL = "You don't have access to this";

    public ForbiddenException(String detail) {
        super(ErrorCode.FORBIDDEN, detail);
    }

    public ForbiddenException() {
        this(DEFAULT_DETAIL);
    }
}
