package com.example.fairnesstracker.dto.alert;

import java.time.LocalDateTime;

public record AlertResponse(
        Long id,
        String engineerName,
        String severity,
        LocalDateTime triggeredAt,
        LocalDateTime resolvedAt,
        String pagerDutyIncidentId,
        Integer incidentNumber,
        String title,
        String serviceName,
        String assignedEngineerName,
        String urgency,
        String source
) {}
