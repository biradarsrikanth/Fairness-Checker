package com.example.fairnesstracker.common.exception;

import com.example.fairnesstracker.common.web.RequestIdFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.List;

/**
 * Body of every error response, as {@code application/problem+json} (RFC 9457 problem details).
 * {@code type}, {@code title}, {@code status}, {@code detail} and {@code instance} are the standard members;
 * {@code code}, {@code errors}, {@code requestId} and {@code timestamp} are our extensions.
 */
@Schema(name = "ApiError", description = "Problem details (RFC 9457) returned for every error")
public record ApiError(
        @Schema(description = "Problem type; always about:blank, see code instead", example = "about:blank")
        String type,
        @Schema(description = "HTTP reason phrase", example = "Bad Request")
        String title,
        @Schema(example = "400")
        int status,
        @Schema(description = "What went wrong, for people; may change, so don't parse it",
                example = "name: must not be blank")
        String detail,
        @Schema(description = "Path of the request that failed", example = "/api/v1/teams")
        String instance,
        @Schema(description = "Stable error category for programs to branch on")
        ErrorCode code,
        @Schema(description = "Problems with individual fields or parameters; empty when none")
        List<FieldError> errors,
        @Schema(description = "Same value as the X-Request-ID response header; quote it when reporting a problem")
        @Nullable String requestId,
        Instant timestamp
) {

    public static final String TYPE = "about:blank";

    /** {@code field} is the JSON property or query parameter; null for problems with the request as a whole. */
    @Schema(name = "FieldError")
    public record FieldError(
            @Schema(example = "name") @Nullable String field,
            @Schema(example = "must not be blank") String message) {
    }

    public static ApiError of(HttpStatusCode status, ErrorCode code, String detail, List<FieldError> errors,
                              HttpServletRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return new ApiError(TYPE, resolved != null ? resolved.getReasonPhrase() : "Error", status.value(), detail,
                path(request), code, List.copyOf(errors), requestId(request), Instant.now());
    }

    // On an error dispatch the request URI is /error; the servlet container keeps the original one
    private static String path(HttpServletRequest request) {
        Object original = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return original != null ? original.toString() : request.getRequestURI();
    }

    // The attribute survives an error dispatch to /error, where the filter's MDC entry is already gone
    private static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return id != null ? id.toString() : MDC.get(RequestIdFilter.MDC_KEY);
    }
}
