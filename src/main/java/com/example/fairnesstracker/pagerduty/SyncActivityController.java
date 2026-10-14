package com.example.fairnesstracker.pagerduty;

import com.example.fairnesstracker.audit.AuditAction;
import com.example.fairnesstracker.audit.AuditService;
import com.example.fairnesstracker.common.exception.ApiException;
import com.example.fairnesstracker.common.exception.ResourceNotFoundException;
import com.example.fairnesstracker.common.web.PageResponse;
import com.example.fairnesstracker.common.web.Sorting;
import com.example.fairnesstracker.pagerduty.dto.SyncActivityDtos.KindStatus;
import com.example.fairnesstracker.pagerduty.dto.SyncActivityDtos.SyncRunFilter;
import com.example.fairnesstracker.pagerduty.dto.SyncActivityDtos.SyncRunResponse;
import com.example.fairnesstracker.pagerduty.dto.SyncActivityDtos.SyncStatusResponse;
import com.example.fairnesstracker.pagerduty.dto.SyncActivityDtos.WebhookEventDetail;
import com.example.fairnesstracker.pagerduty.dto.SyncActivityDtos.WebhookEventFilter;
import com.example.fairnesstracker.pagerduty.dto.SyncActivityDtos.WebhookEventResponse;
import com.example.fairnesstracker.pagerduty.sync.SyncRun;
import com.example.fairnesstracker.pagerduty.sync.SyncRunRepository;
import com.example.fairnesstracker.pagerduty.sync.SyncRunner;
import com.example.fairnesstracker.pagerduty.webhook.WebhookEvent;
import com.example.fairnesstracker.pagerduty.webhook.WebhookEventRepository;
import com.example.fairnesstracker.pagerduty.webhook.WebhookEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.SortDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "PagerDuty sync activity", description = "Sync runs and received webhooks (admins only)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/pagerduty")
public class SyncActivityController {

    private static final Set<String> RUN_SORTS = Set.of("startedAt");
    private static final Set<String> EVENT_SORTS = Set.of("receivedAt");

    private final SyncRunRepository runs;
    private final SyncRunner syncRunner;
    private final WebhookEventRepository events;
    private final WebhookEventService webhookEvents;
    private final SyncActivityMapper mapper;
    private final AuditService audit;
    private final boolean scheduled;
    private final long syncRateMs;

    public SyncActivityController(SyncRunRepository runs, SyncRunner syncRunner, WebhookEventRepository events,
                                  WebhookEventService webhookEvents, SyncActivityMapper mapper, AuditService audit,
                                  @Value("${pagerduty.sync.enabled:true}") boolean scheduled,
                                  @Value("${pagerduty.sync-rate}") long syncRateMs) {
        this.runs = runs;
        this.syncRunner = syncRunner;
        this.events = events;
        this.webhookEvents = webhookEvents;
        this.mapper = mapper;
        this.audit = audit;
        this.scheduled = scheduled;
        this.syncRateMs = syncRateMs;
    }

    @Operation(operationId = "listSyncRuns", summary = "List sync runs", description = "Newest first. Filters combine with AND.")
    @GetMapping("/sync-runs")
    @Transactional(readOnly = true)
    public PageResponse<SyncRunResponse> listRuns(
            @ParameterObject SyncRunFilter filter,
            @ParameterObject @SortDefault(sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        List<Specification<SyncRun>> conditions = new ArrayList<>();
        if (filter.kind() != null) conditions.add((run, query, cb) -> cb.equal(run.get("kind"), filter.kind()));
        if (filter.status() != null) conditions.add((run, query, cb) -> cb.equal(run.get("status"), filter.status()));
        if (filter.trigger() != null) {
            conditions.add((run, query, cb) -> cb.equal(run.get("trigger"), filter.trigger()));
        }
        return PageResponse.from(runs.findAll(Specification.allOf(conditions),
                Sorting.restrict(pageable, RUN_SORTS)).map(mapper::toResponse));
    }

    @Operation(operationId = "getSyncRun")
    @GetMapping("/sync-runs/{id}")
    @Transactional(readOnly = true)
    public SyncRunResponse getRun(@PathVariable long id) {
        return runs.findById(id).map(mapper::toResponse).orElseThrow(() -> ResourceNotFoundException.of("Sync run", id));
    }

    @Operation(operationId = "getSyncStatus", summary = "Latest sync of each kind", description = "Whether one is running, the last run and the "
            + "last successful one.")
    @GetMapping("/sync-status")
    @Transactional(readOnly = true)
    public SyncStatusResponse status() {
        boolean syncing = scheduled && syncRunner.configured();
        return new SyncStatusResponse(syncRunner.configured(), syncing, syncing ? syncRateMs / 1000 : null,
                kindStatus(SyncRun.Kind.INCIDENTS), kindStatus(SyncRun.Kind.ONCALLS));
    }

    @Operation(operationId = "listWebhookEvents", summary = "List received webhooks", description = "Newest first, without their bodies. Filters "
            + "combine with AND.")
    @GetMapping("/webhook-events")
    @Transactional(readOnly = true)
    public PageResponse<WebhookEventResponse> listEvents(
            @Valid @ParameterObject WebhookEventFilter filter,
            @ParameterObject @SortDefault(sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        List<Specification<WebhookEvent>> conditions = new ArrayList<>();
        if (filter.status() != null) {
            conditions.add((event, query, cb) -> cb.equal(event.get("status"), filter.status()));
        }
        if (filter.eventType() != null) {
            conditions.add((event, query, cb) -> cb.equal(event.get("eventType"), filter.eventType()));
        }
        if (filter.incidentId() != null) {
            conditions.add((event, query, cb) -> cb.equal(event.get("incidentId"), filter.incidentId()));
        }
        return PageResponse.from(events.findAll(Specification.allOf(conditions),
                Sorting.restrict(pageable, EVENT_SORTS)).map(mapper::toResponse));
    }

    @Operation(operationId = "getWebhookEvent")
    @GetMapping("/webhook-events/{id}")
    @Transactional(readOnly = true)
    public WebhookEventDetail getEvent(@PathVariable long id) {
        return events.findById(id).map(mapper::toDetail)
                .orElseThrow(() -> ResourceNotFoundException.of("Webhook event", id));
    }

    @Operation(operationId = "replayWebhookEvent", summary = "Apply a received webhook again",
            description = "For a FAILED event, or one stuck in RECEIVED. 409 if it was already applied. Answers "
                    + "with the event's new state, or the error that made it fail again.")
    @PostMapping("/webhook-events/{id}/replay")
    public WebhookEventDetail replayEvent(@PathVariable long id) {
        WebhookEvent replayed;
        try {
            replayed = webhookEvents.replay(id);
        } catch (ApiException e) {
            // Unknown or already applied: nothing ran
            throw e;
        } catch (RuntimeException e) {
            audit.record(AuditAction.WEBHOOK_REPLAYED, "WEBHOOK_EVENT", id, Map.of("status", "FAILED"));
            throw e;
        }
        audit.record(AuditAction.WEBHOOK_REPLAYED, "WEBHOOK_EVENT", id, Map.of("status", replayed.getStatus().name()));
        return mapper.toDetail(replayed);
    }

    private KindStatus kindStatus(SyncRun.Kind kind) {
        SyncRun last = runs.findFirstByKindOrderByStartedAtDesc(kind).orElse(null);
        SyncRun lastSuccess = runs.findFirstByKindAndStatusOrderByStartedAtDesc(kind, SyncRun.Status.SUCCEEDED)
                .orElse(null);
        return new KindStatus(last != null && syncRunner.isRunning(last),
                last != null ? mapper.toResponse(last) : null,
                lastSuccess != null ? mapper.toResponse(lastSuccess) : null);
    }
}
