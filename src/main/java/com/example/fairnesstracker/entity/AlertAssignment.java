package com.example.fairnesstracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One step in an alert's paging history: who was paged, escalated to or reassigned, and when (UTC). */
@Entity
@Table(name = "alert_assignment")
@Getter
@Setter
@NoArgsConstructor
public class AlertAssignment {

    public enum Kind { PAGED, ESCALATED, REASSIGNED }

    /** LOG: PagerDuty incident log (source of truth); WEBHOOK: provisional; BACKFILL: migrated data;
     *  MANUAL: created through our API. */
    public enum Source { LOG, WEBHOOK, BACKFILL, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id")
    private AlertEvent alert;

    // Null when the PagerDuty user isn't one of our engineers
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id")
    private Engineer engineer;

    private String pagerdutyUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(nullable = false)
    private LocalDateTime assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Source source;

    public AlertAssignment(AlertEvent alert, Engineer engineer, String pagerdutyUserId, Kind kind,
                           LocalDateTime assignedAt, Source source) {
        this.alert = alert;
        this.engineer = engineer;
        this.pagerdutyUserId = pagerdutyUserId;
        this.kind = kind;
        this.assignedAt = assignedAt;
        this.source = source;
    }
}
