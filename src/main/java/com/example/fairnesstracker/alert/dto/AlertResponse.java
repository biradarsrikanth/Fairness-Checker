package com.example.fairnesstracker.alert.dto;

import java.time.Instant;

/** {@code engineer*} and {@code team} describe who was paged; null while the alert is unattributed. */
public record AlertResponse(
        Long id,
        Long engineerId,
        String engineerName,
        String team,
        String severity,
        String status,
        String urgency,
        Instant triggeredAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        String pagerDutyIncidentId,
        Integer incidentNumber,
        String title,
        String serviceName,
        String assignedEngineerName,
        String source
) {

    /** One step of the paging history; {@code engineer*} is null when the PagerDuty user isn't one of ours. */
    public record Assignment(String kind, Long engineerId, String engineerName, String pagerDutyUserId,
                             Instant assignedAt, String source) {
    }
}
