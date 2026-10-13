package com.example.fairnesstracker.alert.dto;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** {@code engineer*} and {@code team} describe who was paged; null while the alert is unattributed. */
public record AlertResponse(
        Long id,
        @Nullable Long engineerId,
        @Nullable String engineerName,
        @Nullable String team,
        @Nullable String severity,
        @Nullable String status,
        @Nullable String urgency,
        Instant triggeredAt,
        @Nullable Instant acknowledgedAt,
        @Nullable Instant resolvedAt,
        @Nullable String pagerDutyIncidentId,
        @Nullable Integer incidentNumber,
        @Nullable String title,
        @Nullable String serviceName,
        @Nullable String assignedEngineerName,
        @Nullable String source
) {

    /** One step of the paging history; {@code engineer*} is null when the PagerDuty user isn't one of ours. */
    public record Assignment(String kind, @Nullable Long engineerId, @Nullable String engineerName, @Nullable String pagerDutyUserId,
                             Instant assignedAt, String source) {
    }
}
