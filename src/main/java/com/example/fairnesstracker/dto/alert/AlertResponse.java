package com.example.fairnesstracker.dto.alert;

import com.example.fairnesstracker.entity.AlertAssignment;
import com.example.fairnesstracker.entity.AlertEvent;

import java.time.LocalDateTime;

// Times are UTC
public record AlertResponse(
        Long id,
        Long engineerId,
        String engineerName,
        String team,
        String severity,
        String status,
        String urgency,
        LocalDateTime triggeredAt,
        LocalDateTime acknowledgedAt,
        LocalDateTime resolvedAt,
        String pagerDutyIncidentId,
        Integer incidentNumber,
        String title,
        String serviceName,
        String assignedEngineerName,
        String source
) {

    public static AlertResponse from(AlertEvent alert) {
        var engineer = alert.getEngineer();
        return new AlertResponse(
                alert.getId(),
                engineer != null ? engineer.getId() : null,
                engineer != null ? engineer.getName() : null,
                engineer != null ? engineer.getTeam().getName() : null,
                alert.getSeverity(),
                alert.getStatus(),
                alert.getUrgency(),
                alert.getTriggeredAt(),
                alert.getAcknowledgedAt(),
                alert.getResolvedAt(),
                alert.getPagerDutyIncidentId(),
                alert.getIncidentNumber(),
                alert.getTitle(),
                alert.getService() != null ? alert.getService().getName() : null,
                alert.getAssignedEngineerName(),
                alert.getSource());
    }

    public record Assignment(String kind, Long engineerId, String engineerName, String pagerDutyUserId,
                             LocalDateTime assignedAt, String source) {
        public static Assignment from(AlertAssignment a) {
            var engineer = a.getEngineer();
            return new Assignment(a.getKind().name(),
                    engineer != null ? engineer.getId() : null,
                    engineer != null ? engineer.getName() : null,
                    a.getPagerdutyUserId(), a.getAssignedAt(), a.getSource().name());
        }
    }
}
