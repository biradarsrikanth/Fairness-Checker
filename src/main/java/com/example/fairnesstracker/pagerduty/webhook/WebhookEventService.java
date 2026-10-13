package com.example.fairnesstracker.pagerduty.webhook;

import com.example.fairnesstracker.common.exception.ConflictException;
import com.example.fairnesstracker.common.exception.ResourceNotFoundException;
import com.example.fairnesstracker.pagerduty.PagerDutyService;
import com.example.fairnesstracker.pagerduty.webhook.WebhookEvent.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Applies each PagerDuty webhook event at most once. The event is stored first, under PagerDuty's event id;
 * a delivery of an id already stored is a no-op unless the earlier attempt FAILED.
 *
 * <p>Bookkeeping writes commit on their own, so a failed event stays recorded as FAILED while what it
 * changed rolls back. If the gateway dies between applying an event and marking it, the row stays RECEIVED
 * and a redelivery is skipped; the scheduled sync re-reads the incident, and an admin can replay it.
 */
@Slf4j
@Service
public class WebhookEventService {

    private static final int MAX_ID_LENGTH = 64;
    // A redelivery from PagerDuty: only a failed attempt is worth repeating
    private static final Set<Status> REDELIVERABLE = EnumSet.of(Status.FAILED);
    // An admin may also push through an event stuck in RECEIVED
    private static final Set<Status> REPLAYABLE = EnumSet.of(Status.FAILED, Status.RECEIVED);
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final WebhookEventRepository events;
    private final PagerDutyService pagerDutyService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate ownTransaction;

    public WebhookEventService(WebhookEventRepository events, PagerDutyService pagerDutyService,
                               ObjectMapper objectMapper, Clock clock, PlatformTransactionManager transactionManager) {
        this.events = events;
        this.pagerDutyService = pagerDutyService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Stores and applies a signed webhook body (a JSON object). Returns a short outcome for the response. */
    public String receive(String rawPayload, JsonNode root) {
        JsonNode event = root.path("event");
        String eventId = eventId(event, rawPayload);
        Optional<Long> claimed = storeOrClaim(eventId, event, root);
        if (claimed.isEmpty()) {
            log.info("PagerDuty webhook event {} was already received; skipped", eventId);
            return "Already processed";
        }
        return apply(claimed.get(), event);
    }

    /** Applies a stored event again, e.g. after fixing what made it fail. */
    public WebhookEvent replay(long id) {
        WebhookEvent stored = events.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Webhook event", id));
        if (!claim(id, REPLAYABLE)) {
            throw new ConflictException("Webhook event " + id + " was already applied (" + stored.getStatus() + ")");
        }
        apply(id, objectMapper.valueToTree(stored.getPayload()).path("event"));
        return events.findById(id).orElseThrow();
    }

    private Optional<Long> storeOrClaim(String eventId, JsonNode event, JsonNode root) {
        WebhookEvent received = new WebhookEvent(eventId,
                truncate(text(event, "event_type", "unknown"), MAX_ID_LENGTH),
                truncate(text(event.path("data"), "id", null), MAX_ID_LENGTH),
                clock.instant(), objectMapper.convertValue(root, JSON_OBJECT));
        try {
            return Optional.of(ownTransaction.execute(status -> events.saveAndFlush(received)).getId());
        } catch (DataIntegrityViolationException e) {
            // Seen before (the unique event id)
            WebhookEvent existing = events.findByPagerdutyEventId(eventId).orElseThrow(() -> e);
            return claim(existing.getId(), REDELIVERABLE) ? Optional.of(existing.getId()) : Optional.empty();
        }
    }

    private boolean claim(long id, Set<Status> from) {
        return Integer.valueOf(1).equals(ownTransaction.execute(status -> events.claim(id, from)));
    }

    private String apply(long id, JsonNode event) {
        String outcome;
        try {
            outcome = pagerDutyService.applyWebhookEvent(event);
        } catch (DataIntegrityViolationException e) {
            // The scheduled sync stored the same incident at the same moment; the unique index kept one copy
            finish(id, Status.PROCESSED, null);
            return "Already processed";
        } catch (RuntimeException e) {
            finish(id, Status.FAILED, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
        finish(id, "Ignored".equals(outcome) ? Status.IGNORED : Status.PROCESSED, null);
        log.info("PagerDuty webhook {} for incident {}: {}", event.path("event_type").asString(),
                event.path("data").path("id").asString(), outcome);
        return outcome;
    }

    private void finish(long id, Status status, String error) {
        ownTransaction.executeWithoutResult(tx ->
                events.finish(id, status, clock.instant(), truncate(error, WebhookEvent.MAX_ERROR_LENGTH)));
    }

    // PagerDuty V3 events carry a unique id; without one, an identical body is the same event
    private static String eventId(JsonNode event, String rawPayload) {
        String id = text(event, "id", null);
        if (id != null && !id.isBlank() && id.length() <= MAX_ID_LENGTH) return id;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asString() : fallback;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
