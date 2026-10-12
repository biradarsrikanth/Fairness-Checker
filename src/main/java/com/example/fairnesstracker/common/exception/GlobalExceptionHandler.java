package com.example.fairnesstracker.common.exception;

import com.example.fairnesstracker.common.exception.ApiError.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns every exception into an {@link ApiError}. Spring's own exceptions (unknown route, wrong method,
 * unreadable body, ...) arrive through {@link ResponseEntityExceptionHandler}; ours carry an
 * {@link ErrorCode}. Client errors explain themselves; server errors never reveal internals.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String INTERNAL_DETAIL = "Something went wrong; quote the requestId if you report this";
    private static final String INVALID_VALUE = "has an invalid value";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.getCode().status().is5xxServerError()) {
            log.warn("{}: {}", ex.getCode(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getCode().status())
                .headers(ex.getHeaders())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiError.of(ex.getCode().status(), ex.getCode(), ex.getMessage(), ex.getErrors(), request));
    }

    // @PreAuthorize denials are thrown inside the controller call, so they arrive here rather than at the
    // security filter chain's AccessDeniedHandler; without this the catch-all below would make them 500s
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ErrorCode.FORBIDDEN, ForbiddenException.DEFAULT_DETAIL, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHENTICATED, "Sign in to use this endpoint", request);
    }

    // A constraint rejected the write. Services check the usual cases first, so this is mostly a race with a
    // concurrent request; the constraint name is logged, not returned
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.CONFLICT, "The change conflicts with existing data", request);
    }

    // Safety net for a list endpoint that doesn't restrict its sort fields (see Sorting)
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleUnknownSortProperty(PropertyReferenceException ex,
                                                              HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED.status(), ErrorCode.VALIDATION_FAILED,
                "sort: can't sort by '" + ex.getPropertyName() + "'",
                List.of(new FieldError("sort", "can't sort by '" + ex.getPropertyName() + "'")), request);
    }

    // PagerDuty didn't answer (connection refused, timeout); scorer failures arrive as ScoringServiceException
    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ApiError> handleUpstreamUnavailable(WebClientRequestException ex,
                                                              HttpServletRequest request) {
        log.error("Upstream request failed: {}", ex.getMessage());
        return respond(ErrorCode.UPSTREAM_UNAVAILABLE, "Upstream service is unavailable", request);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiError> handleUpstreamError(WebClientResponseException ex,
                                                        HttpServletRequest request) {
        log.error("Upstream returned {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        return respond(ErrorCode.UPSTREAM_ERROR, "Upstream service returned an error", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error", ex);
        return respond(ErrorCode.INTERNAL_ERROR, INTERNAL_DETAIL, request);
    }

    // ---------------------------------------------------------------------------------------------------
    // Spring MVC exceptions with field-level detail
    // ---------------------------------------------------------------------------------------------------

    // @Valid on a request body or a bound object (e.g. query parameters into a record)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return validationFailed(fieldErrors(ex.getBindingResult()), headers, request);
    }

    // Constraints on @PathVariable/@RequestParam (e.g. @Positive), and @Valid objects of such a method
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bound) {
                errors.addAll(fieldErrors(bound));
            } else {
                String parameter = parameterName(result.getMethodParameter());
                result.getResolvableErrors().forEach(e -> errors.add(new FieldError(parameter, message(e))));
            }
        }
        ex.getCrossParameterValidationResults().forEach(e -> errors.add(new FieldError(null, message(e))));
        return validationFailed(errors, headers, request);
    }

    // e.g. ?start=not-a-date or /engineers/abc
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return validationFailed(List.of(new FieldError(name, INVALID_VALUE)), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        return validationFailed(List.of(new FieldError(ex.getParameterName(), "is required")), headers, request);
    }

    // A JSON value of the wrong type names its field; anything else (empty body, broken JSON) can't
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        if (ex.getMostSpecificCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            return validationFailed(List.of(new FieldError(jsonPath(mismatch), INVALID_VALUE)), headers, request);
        }
        return toObjectResponse(status, ErrorCode.MALFORMED_REQUEST,
                "Request body is missing or isn't valid JSON", List.of(), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
                                                                    HttpStatusCode status, WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        return toObjectResponse(status, ErrorCode.NOT_FOUND,
                "No endpoint " + servletRequest.getMethod() + " " + servletRequest.getRequestURI(), List.of(), headers,
                request);
    }

    // Every other Spring MVC exception: keep Spring's status, headers (e.g. Allow) and its client-safe detail
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String detail;
        if (statusCode.is5xxServerError()) {
            log.error("Request failed", ex);
            detail = INTERNAL_DETAIL;
        } else {
            detail = body instanceof ProblemDetail problem && problem.getDetail() != null
                    ? problem.getDetail()
                    : ErrorCode.forStatus(statusCode).name();
        }
        return toObjectResponse(statusCode, ErrorCode.forStatus(statusCode), detail, List.of(), headers, request);
    }

    // ---------------------------------------------------------------------------------------------------

    private ResponseEntity<Object> validationFailed(List<FieldError> errors, HttpHeaders headers, WebRequest request) {
        List<FieldError> sorted = errors.stream()
                .sorted(Comparator.comparing(FieldError::field, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(FieldError::message))
                .toList();
        String detail = sorted.stream()
                .map(e -> e.field() != null ? e.field() + ": " + e.message() : e.message())
                .collect(Collectors.joining("; "));
        return toObjectResponse(ErrorCode.VALIDATION_FAILED.status(), ErrorCode.VALIDATION_FAILED, detail, sorted,
                headers, request);
    }

    private static List<FieldError> fieldErrors(BindingResult result) {
        List<FieldError> errors = new ArrayList<>();
        // Binding failures (e.g. "abc" for a number) carry Java type names in their message; don't show those
        result.getFieldErrors().forEach(e ->
                errors.add(new FieldError(e.getField(), e.isBindingFailure() ? INVALID_VALUE : message(e))));
        result.getGlobalErrors().forEach(e -> errors.add(new FieldError(null, message(e))));
        return errors;
    }

    private static List<FieldError> fieldErrors(ParameterErrors result) {
        List<FieldError> errors = new ArrayList<>();
        result.getFieldErrors().forEach(e ->
                errors.add(new FieldError(e.getField(), e.isBindingFailure() ? INVALID_VALUE : message(e))));
        result.getGlobalErrors().forEach(e -> errors.add(new FieldError(null, message(e))));
        return errors;
    }

    // The name the client used: @RequestParam("x")/@PathVariable("x") if given, else the Java parameter name
    private static String parameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) return firstNonEmpty(requestParam.value(), requestParam.name(), parameter);
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) return firstNonEmpty(pathVariable.value(), pathVariable.name(), parameter);
        return parameter.getParameterName();
    }

    // value and name are aliases; the raw annotation only has the one that was written
    private static String firstNonEmpty(String value, String name, MethodParameter parameter) {
        if (!value.isEmpty()) return value;
        return !name.isEmpty() ? name : parameter.getParameterName();
    }

    private static String message(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : INVALID_VALUE;
    }

    // e.g. "engineerId" or "items[2].name"
    private static String jsonPath(JacksonException ex) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference ref : ex.getPath()) {
            if (ref.getPropertyName() != null) {
                if (!path.isEmpty()) path.append('.');
                path.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static ResponseEntity<ApiError> respond(ErrorCode code, String detail, HttpServletRequest request) {
        return respond(code.status(), code, detail, List.of(), request);
    }

    private static ResponseEntity<ApiError> respond(HttpStatusCode status, ErrorCode code, String detail,
                                                    List<FieldError> errors, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiError.of(status, code, detail, errors, request));
    }

    private static ResponseEntity<Object> toObjectResponse(HttpStatusCode status, ErrorCode code, String detail,
                                                           List<FieldError> errors, HttpHeaders headers,
                                                           WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return ResponseEntity.status(status).headers(responseHeaders)
                .body(ApiError.of(status, code, detail, errors, servletRequest));
    }
}
