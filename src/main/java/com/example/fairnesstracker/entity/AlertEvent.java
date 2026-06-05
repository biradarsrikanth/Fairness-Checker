package com.example.fairnesstracker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Entity
@Table(name = "alret_event")
@Data
@NoArgsConstructor
@Validated

//The table that records the alerts that are triggered
public class AlertEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    private String pagerDutyIncidentId;
    private String status;

    @NotNull(message = "Requires trigger Time")
    private LocalDateTime triggeredAt;
    private LocalDateTime resolvedAt;

    @Pattern(regexp = "P1|P2|P3", message = "Severity must be P1, P2, or P3")
    private String severity;

    @ManyToOne
    @JoinColumn(name = "engineer_id")
    private Engineer engineer;
}
