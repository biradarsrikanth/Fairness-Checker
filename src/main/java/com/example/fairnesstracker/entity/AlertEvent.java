package com.example.fairnesstracker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_event")
@Data
@NoArgsConstructor
@Validated

public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    // PagerDuty Incident Details
    private String pagerDutyIncidentId;
    private Integer incidentNumber;

    private String title;
    private String status;

    @NotNull(message = "Requires trigger Time")
    private LocalDateTime triggeredAt;

    private LocalDateTime resolvedAt;

    @Pattern(
            regexp = "P1|P2|P3|P4|P5",
            message = "Severity must be P1, P2, P3, P4 or P5"
    )
    private String severity;

    private String urgency;

    // Service Information
    private String serviceId;
    private String serviceName;

    // Assignee Information
    private String pagerDutyUserId;
    private String assignedEngineerName;

    // Source of record (WEBHOOK, API_SYNC, SCHEDULED_SYNC)
    private String source;

    @ManyToOne
    @JoinColumn(name = "engineer_id")
    private Engineer engineer;
}