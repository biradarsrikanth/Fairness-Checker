package com.example.fairnesstracker.common.exception;

import java.util.List;

/**
 * The request is valid but conflicts with existing data (409): a duplicate (name the field) or an operation
 * the current state doesn't allow, e.g. deleting a team that still has engineers.
 */
public class ConflictException extends ApiException {

    public ConflictException(String detail) {
        super(ErrorCode.CONFLICT, detail);
    }

    private ConflictException(String detail, List<ApiError.FieldError> errors) {
        super(ErrorCode.CONFLICT, detail, errors);
    }

    public static ConflictException onField(String field, String message) {
        return new ConflictException(message, List.of(new ApiError.FieldError(field, message)));
    }
}
