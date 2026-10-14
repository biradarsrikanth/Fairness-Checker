package com.example.fairnesstracker.common.web;

import com.example.fairnesstracker.common.exception.ApiError;
import com.example.fairnesstracker.common.exception.ErrorCode;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.coyote.ActionCode;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tomcat refuses some requests before Spring sees them: a URI it can't decode ({@code %zz}), characters a URI
 * can't hold, a malformed request line. It answers those with its own HTML page. This puts the API's
 * problem-details body there instead, so every error a client gets has the same shape.
 */
@Configuration(proxyBeanMethods = false)
public class TomcatErrorReports {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> jsonErrorReports() {
        // The host isn't started yet when contexts are added, so it creates our valve instead of its own
        return factory -> factory.addContextCustomizers(context -> {
            if (context.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(JsonErrorReportValve.class.getName());
            }
        });
    }

    /**
     * Writes {@link ApiError} for errors that reach Tomcat's error report with no body yet. Errors the app
     * already answered (anything Spring handled) have a body, so they're left as they are. Tomcat creates
     * this by class name, hence the public no-argument constructor and its own JSON mapper.
     */
    public static class JsonErrorReportValve extends ErrorReportValve {

        private static final JsonMapper JSON = JsonMapper.builder().build();

        @Override
        protected void report(Request request, Response response, Throwable throwable) {
            int status = response.getStatus();
            if (status < 400 || response.getContentWritten() > 0) {
                return;
            }
            AtomicBoolean ioAllowed = new AtomicBoolean(true);
            response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, ioAllowed);
            if (!ioAllowed.get()) {
                return;
            }
            HttpStatus httpStatus = HttpStatus.resolve(status);
            ErrorCode code = codeFor(status);
            try {
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                byte[] body = JSON.writeValueAsBytes(ApiError.of(
                        httpStatus != null ? httpStatus : HttpStatus.valueOf(code.status().value()),
                        code, detailFor(status), List.of(), request));
                response.getOutputStream().write(body);
                response.finishResponse();
            } catch (IOException | IllegalStateException e) {
                // The client went away or the response is unusable; nothing more to say
                container.getLogger().debug("Couldn't write the error report", e);
            }
        }

        static ErrorCode codeFor(int status) {
            if (status == HttpStatus.BAD_REQUEST.value()) {
                return ErrorCode.MALFORMED_REQUEST;
            }
            // The first code for this status, else the generic one for its class
            return Arrays.stream(ErrorCode.values())
                    .filter(code -> code.status().value() == status)
                    .findFirst()
                    .orElse(status >= 500 ? ErrorCode.INTERNAL_ERROR : ErrorCode.BAD_REQUEST);
        }

        static String detailFor(int status) {
            if (status == HttpStatus.BAD_REQUEST.value()) {
                return "The request couldn't be read: the URL has characters it can't contain or can't be decoded";
            }
            if (status >= 500) {
                return "Something went wrong on our side";
            }
            return "The request failed";
        }
    }
}
