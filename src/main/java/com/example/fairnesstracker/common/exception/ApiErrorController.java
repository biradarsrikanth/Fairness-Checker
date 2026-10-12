package com.example.fairnesstracker.common.exception;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Errors that never reached a controller (thrown by a servlet filter, or sent with response.sendError) are
 * forwarded by the servlet container to /error. Replaces Spring Boot's default error controller so they get
 * the same {@link ApiError} body as everything else.
 */
@Slf4j
@Hidden
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        HttpStatus status = status(request);
        String detail;
        if (status.is5xxServerError()) {
            Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            log.error("Request failed before reaching a controller", exception instanceof Throwable t ? t : null);
            detail = GlobalExceptionHandler.INTERNAL_DETAIL;
        } else {
            detail = status.getReasonPhrase();
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiError.of(status, ErrorCode.forStatus(status), detail, List.of(), request));
    }

    // Without the container's status attribute someone called /error directly: there's nothing here
    private static HttpStatus status(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code == null) return HttpStatus.NOT_FOUND;
        HttpStatus status = code instanceof Integer value ? HttpStatus.resolve(value) : null;
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
