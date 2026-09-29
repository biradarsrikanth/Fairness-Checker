package com.example.fairnesstracker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

// All timestamps are UTC. Allowed values are also enforced by CHECK constraints (V3 migration)
@Entity
@Table(name = "alert_event")
@Data
@NoArgsConstructor
@Validated

public class AlertEvent {

    public static final String STATUS_TRIGGERED = "triggered";
    public static final String STATUS_ACKNOWLEDGED = "acknowledged";
    public static final String STATUS_RESOLVED = "resolved";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    // PagerDuty Incident Details
    @Column(unique = true)
    private String pagerDutyIncidentId;
    private Integer incidentNumber;

    private String title;

    @Pattern(regexp = "triggered|acknowledged|resolved")
    @Column(nullable = false)
    private String status = STATUS_TRIGGERED;

    @NotNull(message = "Requires trigger Time")
    private LocalDateTime triggeredAt;

    private LocalDateTime acknowledgedAt;

    private LocalDateTime resolvedAt;

    @Pattern(
            regexp = "P1|P2|P3|P4|P5",
            message = "Severity must be P1, P2, P3, P4 or P5"
    )
    private String severity;

    @Pattern(regexp = "high|low")
    private String urgency;

    @ManyToOne
    @JoinColumn(name = "service_id")
    private MonitoredService service;

    // Who was paged first; the full history is in alert_assignment
    private String pagerDutyUserId;
    private String assignedEngineerName;

    // Source of record: API (imported), API_SYNC, WEBHOOK or MANUAL
    @Pattern(regexp = "API|API_SYNC|WEBHOOK|MANUAL")
    private String source;

    @ManyToOne
    @JoinColumn(name = "engineer_id")
    private Engineer engineer;
}
