package com.example.fairnesstracker.common.mapping;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Event times are stored as UTC wall-clock times (LocalDateTime, matching the timestamp columns). The API
 * returns them as instants ("2026-09-27T18:00:00Z"): without the Z, browsers read them as local time.
 */
public final class UtcTimes {

    private UtcTimes() {
    }

    public static Instant toInstant(LocalDateTime utc) {
        return utc != null ? utc.toInstant(ZoneOffset.UTC) : null;
    }
}
