package com.example.fairnesstracker.dto;

import lombok.Data;

@Data
public class AlertEventImportDto {

    private String pagerDutyIncidentId;
    private Integer incidentNumber;
    private String title;
    private String status;
    private String triggeredAt;
    private String resolvedAt;
    private String serviceId;
    private String urgency;
    private String severity;
    private String serviceName;
    private String pagerDutyUserId;
    private String assignedEngineerName;
    private String source;
}