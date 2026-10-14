package com.example.fairnesstracker.security;

import com.example.fairnesstracker.common.exception.ApiException;
import com.example.fairnesstracker.common.exception.ErrorCode;

/**
 * A valid access token whose account was disabled or deleted after the token was issued. Answered 401, like
 * an expired token: the client's refresh then fails too (disabling revokes the refresh tokens), so it signs out.
 */
public class InactiveAccountException extends ApiException {

    InactiveAccountException(String detail) {
        super(ErrorCode.UNAUTHENTICATED, detail);
    }
}
