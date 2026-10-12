package com.example.fairnesstracker.common.exception;

import java.util.List;

/**
 * A scorer call failed. The {@link ErrorCode} is what the gateway reports to its caller;
 * {@code upstreamStatus} is what the scorer returned (0 when it never answered), which drives retries.
 */
public class ScoringServiceException extends ApiException {

    private final int upstreamStatus;

    public ScoringServiceException(ErrorCode code, int upstreamStatus, String detail) {
        this(code, upstreamStatus, detail, List.of());
    }

    public ScoringServiceException(ErrorCode code, int upstreamStatus, String detail, List<ApiError.FieldError> errors) {
        super(code, detail, errors);
        this.upstreamStatus = upstreamStatus;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
