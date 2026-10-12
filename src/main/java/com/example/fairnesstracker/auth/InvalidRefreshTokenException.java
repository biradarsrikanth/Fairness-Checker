package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.common.exception.ApiException;
import com.example.fairnesstracker.common.exception.ErrorCode;

/** 401: the client must sign in again. Doesn't say which check failed beyond the code. */
public class InvalidRefreshTokenException extends ApiException {

    private InvalidRefreshTokenException(ErrorCode code, String detail) {
        super(code, detail);
    }

    static InvalidRefreshTokenException invalid() {
        return new InvalidRefreshTokenException(ErrorCode.REFRESH_TOKEN_INVALID,
                "Session expired or ended; sign in again");
    }

    static InvalidRefreshTokenException reused() {
        return new InvalidRefreshTokenException(ErrorCode.REFRESH_TOKEN_REUSED,
                "This session was ended because its refresh token was used twice; sign in again");
    }
}
