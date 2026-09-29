package com.example.fairnesstracker.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    // A database constraint (unique email, foreign key, CHECK) rejected the write
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "CONFLICT", "The change conflicts with existing data");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                // Conversion failures (e.g. start=not-a-date) carry Java type names; don't show those
                .map(error -> error.getField() + ": "
                        + (error.isBindingFailure() ? "invalid value" : error.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    // Constraint on a @PathVariable / @RequestParam, e.g. @Positive
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    // e.g. ?start=not-a-date or /engineers/abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid value for '" + ex.getName() + "'");
    }

    // Status is already translated into a gateway status by ScoringClient
    @ExceptionHandler(ScoringServiceException.class)
    public ResponseEntity<ApiError> handleScoringServiceException(ScoringServiceException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatus());
        return respond(status, status.name(), ex.getMessage());
    }

    // An upstream (PagerDuty) call failed to connect or timed out
    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ApiError> handleUpstreamUnavailable(WebClientRequestException ex) {
        log.error("Upstream request failed: {}", ex.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Upstream service is unavailable");
    }

    // An upstream (PagerDuty) call returned an error
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiError> handleUpstreamError(WebClientResponseException ex) {
        log.error("Upstream returned {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        return respond(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", "Upstream service returned an error");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        // Spring's own client errors (unknown path, wrong method, missing parameter) keep their status
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return respond(status, status.name(), errorResponse.getBody().getDetail());
        }
        log.error("Unhandled error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Something went wrong");
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), error, message));
    }
}
