package com.example.fairnesstracker.pagerduty.sync;

import com.example.fairnesstracker.common.exception.ApiException;
import com.example.fairnesstracker.common.exception.ErrorCode;

/** A manual sync was asked for while the same kind of sync is running (here or on another instance). */
public class SyncInProgressException extends ApiException {

    public SyncInProgressException(SyncRun.Kind kind) {
        super(ErrorCode.SYNC_IN_PROGRESS, switch (kind) {
            case INCIDENTS -> "An incident sync is already running; try again when it finishes";
            case ONCALLS -> "An on-call sync is already running; try again when it finishes";
        });
    }
}
