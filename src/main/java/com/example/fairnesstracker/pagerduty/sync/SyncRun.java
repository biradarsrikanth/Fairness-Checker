package com.example.fairnesstracker.pagerduty.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One PagerDuty sync, scheduled or manual: RUNNING while it works, then SUCCEEDED or FAILED. For ONCALLS,
 * {@code createdCount} is the number of shifts stored (new or merged) and {@code updatedCount} stays null.
 */
@Entity
@Table(name = "sync_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncRun {

    public enum Kind { INCIDENTS, ONCALLS }

    public enum Trigger { SCHEDULED, MANUAL }

    public enum Status { RUNNING, SUCCEEDED, FAILED }

    static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 16, updatable = false)
    private Trigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant finishedAt;

    private Integer createdCount;

    private Integer updatedCount;

    @Column(length = MAX_ERROR_LENGTH)
    private String error;

    // The admin who started a manual sync; a plain id, so the run outlives the user
    @Column(updatable = false)
    private Long triggeredBy;

    SyncRun(Kind kind, Trigger trigger, Instant startedAt, Long triggeredBy) {
        this.kind = kind;
        this.trigger = trigger;
        this.status = Status.RUNNING;
        this.startedAt = startedAt;
        this.triggeredBy = triggeredBy;
    }

    void succeeded(Instant at, int created, Integer updated, String note) {
        finish(Status.SUCCEEDED, at, note);
        this.createdCount = created;
        this.updatedCount = updated;
    }

    void failed(Instant at, String error) {
        finish(Status.FAILED, at, error);
    }

    private void finish(Status status, Instant at, String error) {
        this.status = status;
        // The table rejects a finish before the start (clock adjustments)
        this.finishedAt = at.isBefore(startedAt) ? startedAt : at;
        this.error = truncate(error);
    }

    static String truncate(String value) {
        return value == null || value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
