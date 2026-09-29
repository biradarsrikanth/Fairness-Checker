package com.example.fairnesstracker.dto.pagerDuty;

/**
 * Outcome of one PagerDuty incident sync. {@code unattributed} counts incidents whose paged user has no
 * matching engineer; they are stored but not scored until an engineer with that PagerDuty id exists.
 */
public record SyncResult(int created, int updated, int unattributed, int failed) {

    /** Outcome of one on-call sync. {@code skipped} are shifts of PagerDuty users who aren't our engineers. */
    public record Oncall(int shiftsStored, int skipped) {
    }
}
