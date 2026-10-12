package com.example.fairnesstracker.common.exception;

import java.util.List;

/**
 * Input that passes bean validation but is still wrong (400), e.g. an unknown team or timezone. Name the
 * field when there is one, so a form can show the message next to it.
 */
public class ValidationException extends ApiException {

    public ValidationException(String detail) {
        super(ErrorCode.VALIDATION_FAILED, detail);
    }

    private ValidationException(String detail, List<ApiError.FieldError> errors) {
        super(ErrorCode.VALIDATION_FAILED, detail, errors);
    }

    public static ValidationException onField(String field, String message) {
        return new ValidationException(message, List.of(new ApiError.FieldError(field, message)));
    }
}
