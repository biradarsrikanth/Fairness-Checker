package com.example.fairnesstracker.pagerduty.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * One signed PagerDuty webhook, stored before it's applied. The unique event id is what makes a redelivery a
 * no-op: RECEIVED while it's applied, then PROCESSED, IGNORED (not an incident event) or FAILED (applied again
 * on PagerDuty's retry, or replayed by an admin).
 */
@Entity
@Table(name = "webhook_event",
        uniqueConstraints = @UniqueConstraint(name = "uq_webhook_event", columnNames = "pagerduty_event_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent {

    public enum Status { RECEIVED, PROCESSED, IGNORED, FAILED }

    static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pagerduty_event_id", nullable = false, length = 64, updatable = false)
    private String pagerdutyEventId;

    @Column(nullable = false, length = 64, updatable = false)
    private String eventType;

    @Column(length = 64, updatable = false)
    private String incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    private Instant processedAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String error;

    // The whole request body, as PagerDuty sent it
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> payload;

    WebhookEvent(String pagerdutyEventId, String eventType, String incidentId, Instant receivedAt,
                 Map<String, Object> payload) {
        this.pagerdutyEventId = pagerdutyEventId;
        this.eventType = eventType;
        this.incidentId = incidentId;
        this.status = Status.RECEIVED;
        this.receivedAt = receivedAt;
        this.payload = payload;
    }
}
