package com.example.fairnesstracker.common.exception;

/** The resource addressed by the URL doesn't exist (404). A missing reference inside a body is a ValidationException. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String detail) {
        super(ErrorCode.NOT_FOUND, detail);
    }

    /** e.g. {@code of("Team", 5)} → "Team 5 not found". */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " " + id + " not found");
    }
}
