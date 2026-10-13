package com.example.fairnesstracker.pagerduty.dto;

import com.example.fairnesstracker.pagerduty.sync.SyncRun;
import com.example.fairnesstracker.pagerduty.webhook.WebhookEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/** Sync runs and received webhooks, as admins see them. */
public final class SyncActivityDtos {

    private SyncActivityDtos() {
    }

    @Schema(description = "One PagerDuty sync. For ONCALLS, createdCount is the number of shifts stored "
            + "(new or merged) and updatedCount is null. A SUCCEEDED run may still name an error: incidents "
            + "that couldn't be stored.")
    public record SyncRunResponse(
            Long id,
            SyncRun.Kind kind,
            SyncRun.Trigger trigger,
            SyncRun.Status status,
            Instant startedAt,
            @Schema(description = "Null while RUNNING") Instant finishedAt,
            @Schema(description = "Null while RUNNING") Long durationMs,
            Integer createdCount,
            Integer updatedCount,
            String error,
            @Schema(description = "The admin who started a MANUAL run") Long triggeredBy) {
    }

    /** Optional filters, combined with AND. */
    public record SyncRunFilter(SyncRun.Kind kind, SyncRun.Status status, SyncRun.Trigger trigger) {
    }

    @Schema(description = "The latest sync of each kind, for a \"last synced 4 minutes ago\" display")
    public record SyncStatusResponse(
            @Schema(description = "Whether this instance runs the scheduled sync") boolean scheduled,
            @Schema(description = "Time between scheduled syncs; null when not scheduled") Long intervalSeconds,
            KindStatus incidents,
            KindStatus oncalls) {
    }

    public record KindStatus(
            @Schema(description = "Whether a sync of this kind is running now, on any instance") boolean running,
            @Schema(description = "Null before the first sync") SyncRunResponse lastRun,
            @Schema(description = "Null before the first successful sync") SyncRunResponse lastSuccess) {
    }

    @Schema(description = "One received PagerDuty webhook (without its body; see GET /webhook-events/{id})")
    public record WebhookEventResponse(
            Long id,
            String pagerdutyEventId,
            String eventType,
            String incidentId,
            WebhookEvent.Status status,
            Instant receivedAt,
            Instant processedAt,
            String error) {
    }

    @Schema(description = "One received PagerDuty webhook with the body PagerDuty sent")
    public record WebhookEventDetail(
            Long id,
            String pagerdutyEventId,
            String eventType,
            String incidentId,
            WebhookEvent.Status status,
            Instant receivedAt,
            Instant processedAt,
            String error,
            Map<String, Object> payload) {
    }

    /** Optional filters, combined with AND. */
    public record WebhookEventFilter(
            WebhookEvent.Status status,
            @Schema(example = "incident.triggered") @Size(max = 64) String eventType,
            @Size(max = 64) String incidentId) {
    }
}
