package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.common.exception.ApiException;
import com.example.fairnesstracker.common.exception.ErrorCode;
import org.springframework.http.HttpHeaders;

import java.time.Duration;

/** Sign-in refused. Wrong email and wrong password look the same, so neither reveals which accounts exist. */
public class AuthenticationFailedException extends ApiException {

    private final Duration retryAfter;

    private AuthenticationFailedException(ErrorCode code, String detail, Duration retryAfter) {
        super(code, detail);
        this.retryAfter = retryAfter;
    }

    static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException(ErrorCode.INVALID_CREDENTIALS, "Wrong email or password", null);
    }

    static AuthenticationFailedException disabled() {
        return new AuthenticationFailedException(ErrorCode.ACCOUNT_DISABLED,
                "This account is disabled; ask an admin", null);
    }

    static AuthenticationFailedException locked(Duration retryAfter) {
        long minutes = Math.max(1, (retryAfter.toSeconds() + 59) / 60);
        return new AuthenticationFailedException(ErrorCode.ACCOUNT_LOCKED,
                "Too many failed sign-ins; try again in " + minutes + (minutes == 1 ? " minute" : " minutes"),
                retryAfter);
    }

    static AuthenticationFailedException unknownUser() {
        return new AuthenticationFailedException(ErrorCode.UNAUTHENTICATED,
                "This account no longer exists; sign in again", null);
    }

    @Override
    public HttpHeaders getHeaders() {
        if (retryAfter == null) {
            return HttpHeaders.EMPTY;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, retryAfter.toSeconds())));
        return headers;
    }
}
