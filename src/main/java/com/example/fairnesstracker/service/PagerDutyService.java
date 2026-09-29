package com.example.fairnesstracker.service;

import com.example.fairnesstracker.dto.pagerDuty.SyncResult;
import com.example.fairnesstracker.dto.pagerDuty.incident.Assignee;
import com.example.fairnesstracker.dto.pagerDuty.incident.Assignment;
import com.example.fairnesstracker.dto.pagerDuty.incident.IncidentResponse;
import com.example.fairnesstracker.dto.pagerDuty.incident.LastStatusChangeBy;
import com.example.fairnesstracker.dto.pagerDuty.incident.LogEntriesResponse;
import com.example.fairnesstracker.dto.pagerDuty.incident.LogEntriesResponse.LogEntry;
import com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyIncident;
import com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyResponse;
import com.example.fairnesstracker.dto.pagerDuty.incident.ServiceInfo;
import com.example.fairnesstracker.dto.pagerDuty.user.PagerDutyUsersResponse;
import com.example.fairnesstracker.entity.AlertAssignment;
import com.example.fairnesstracker.entity.AlertAssignment.Kind;
import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.entity.MonitoredService;
import com.example.fairnesstracker.repository.AlertAssignmentRepository;
import com.example.fairnesstracker.repository.AlertRepository;
import com.example.fairnesstracker.repository.EngineerRepository;
import com.example.fairnesstracker.repository.MonitoredServiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pulls incidents from PagerDuty and applies webhook events, storing one {@link AlertEvent} per incident,
 * its paging history ({@link AlertAssignment}) and the service that raised it.
 *
 * <p>Attribution: an alert belongs to the engineer who was <em>paged</em> (first assignee, or the first
 * assignment in the incident log once PagerDuty has cleared assignments), not whoever resolved it.
 * It is decided when the alert is created and never overwritten.
 *
 * <p>The incident log is read when an incident is new or its status changed; it replaces the alert's
 * paging history and supplies the acknowledgement time. Webhook rows are provisional until then.
 */
@Slf4j
@Service
public class PagerDutyService {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_TEXT_LENGTH = 255;
    private static final Pattern SEVERITY = Pattern.compile("P[1-5]");

    private final WebClient webClient;
    private final AlertRepository alertRepository;
    private final EngineerRepository engineerRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final AlertAssignmentRepository assignmentRepository;
    private final TransactionOperations transactions;
    private final int lookbackDays;

    @Autowired
    public PagerDutyService(@Qualifier("pagerDutyClient") WebClient webClient,
                            AlertRepository alertRepository,
                            EngineerRepository engineerRepository,
                            MonitoredServiceRepository serviceRepository,
                            AlertAssignmentRepository assignmentRepository,
                            TransactionOperations transactions,
                            @Value("${pagerduty.sync.lookback-days}") int lookbackDays) {
        this.webClient = webClient;
        this.alertRepository = alertRepository;
        this.engineerRepository = engineerRepository;
        this.serviceRepository = serviceRepository;
        this.assignmentRepository = assignmentRepository;
        this.transactions = transactions;
        this.lookbackDays = lookbackDays;
    }

    public PagerDutyResponse getIncidents(int offset, int limit) {
        return webClient.get()
                .uri(b -> b.path("/incidents").queryParam("offset", offset).queryParam("limit", limit).build())
                .retrieve()
                .bodyToMono(PagerDutyResponse.class)
                .block();
    }

    public PagerDutyUsersResponse getUsers() {
        return webClient.get().uri("/users").retrieve().bodyToMono(PagerDutyUsersResponse.class).block();
    }

    /** Re-reads incidents created in the lookback window; a safety net for missed webhooks. */
    public SyncResult syncIncidents() {
        OffsetDateTime until = OffsetDateTime.now(ZoneOffset.UTC);
        String since = until.minusDays(lookbackDays).toInstant().toString();
        int created = 0, updated = 0, unattributed = 0, failed = 0;
        int offset = 0;
        boolean more;

        do {
            final int pageOffset = offset;
            PagerDutyResponse page = webClient.get()
                    .uri(b -> b.path("/incidents")
                            .queryParam("since", since)
                            .queryParam("until", until.toInstant().toString())
                            .queryParam("time_zone", "UTC")
                            .queryParam("offset", pageOffset)
                            .queryParam("limit", PAGE_SIZE)
                            .build())
                    .retrieve()
                    .bodyToMono(PagerDutyResponse.class)
                    .block();
            if (page == null || page.getIncidents() == null) break;

            for (PagerDutyIncident incident : page.getIncidents()) {
                try {
                    // One transaction per incident: a bad incident doesn't roll back the others
                    Upsert result = transactions.execute(status -> upsert(incident));
                    if (result == null) continue;
                    if (result.created()) created++; else updated++;
                    if (!result.attributed()) unattributed++;
                } catch (RuntimeException e) {
                    failed++;
                    log.error("Failed to sync PagerDuty incident {}", incident.getId(), e);
                }
            }
            more = page.isMore();
            offset += PAGE_SIZE;
        } while (more);

        SyncResult result = new SyncResult(created, updated, unattributed, failed);
        log.info("PagerDuty incident sync finished (lookback {} days): {}", lookbackDays, result);
        return result;
    }

    public void resolveIncident(String incidentId, String email) {
        Map<String, Object> body = Map.of("incident", Map.of("type", "incident_reference", "status", "resolved"));
        webClient.put()
                .uri("/incidents/{id}", incidentId)
                .header("From", email)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.createException())
                .toBodilessEntity()
                .block();
        refreshIncident(incidentId);
    }

    /** Re-reads one incident from PagerDuty and stores the result. */
    public void refreshIncident(String incidentId) {
        IncidentResponse response = webClient.get()
                .uri("/incidents/{id}", incidentId)
                .retrieve()
                .bodyToMono(IncidentResponse.class)
                .block();
        if (response != null && response.getIncident() != null) {
            transactions.executeWithoutResult(status -> upsert(response.getIncident()));
        }
    }

    Upsert upsert(PagerDutyIncident incident) {
        Optional<AlertEvent> existing = alertRepository.findByPagerDutyIncidentId(incident.getId());
        AlertEvent alert = existing.orElseGet(AlertEvent::new);
        boolean isNew = existing.isEmpty();
        boolean statusChanged = isNew || !Objects.equals(alert.getStatus(), incident.getStatus());

        if (isNew) {
            alert.setPagerDutyIncidentId(incident.getId());
            alert.setTriggeredAt(toUtc(incident.getCreatedAt()));
            alert.setSource("API_SYNC");
        }
        alert.setStatus(incident.getStatus());
        alert.setTitle(truncate(incident.getTitle()));
        if (incident.getIncidentNumber() != null) alert.setIncidentNumber(incident.getIncidentNumber());
        alert.setUrgency(incident.getUrgency());
        alert.setSeverity(severity(incident.getPriority() != null ? incident.getPriority().getSummary() : null,
                incident.getUrgency()));
        alert.setService(resolveService(incident.getService()));
        if (incident.getResolvedAt() != null) {
            alert.setResolvedAt(notBefore(toUtc(incident.getResolvedAt()), alert.getTriggeredAt()));
        }

        // The log costs one API call, so only read it when something happened
        List<LogEntry> incidentLog = statusChanged ? fetchLog(incident.getId()) : null;

        if (alert.getEngineer() == null) {
            if (isNew) {
                Assignee paged = firstAssignee(incident.getAssignments());
                if (paged == null && incidentLog != null) paged = firstPagedFromLog(incidentLog);
                attribute(alert, paged, incident.getId(), incident.getLastStatusChangeBy());
            } else {
                // The engineer may have been added since the alert was stored
                linkEngineer(alert);
            }
        }
        if (incidentLog != null) applyAcknowledgement(alert, incidentLog);

        AlertEvent saved = alertRepository.save(alert);
        if (incidentLog != null) {
            replaceAssignments(saved, incidentLog);
        } else if (isNew) {
            recordProvisionalPage(saved);
        }
        return new Upsert(isNew, saved.getEngineer() != null);
    }

    /** Applies a PagerDuty V3 webhook event. Returns a short description for the response body. */
    @org.springframework.transaction.annotation.Transactional
    public String applyWebhookEvent(JsonNode event) {
        String type = event.path("event_type").asText("");
        JsonNode data = event.path("data");
        String incidentId = text(data, "id");
        if (!type.startsWith("incident.") || incidentId == null) {
            return "Ignored";
        }
        LocalDateTime occurredAt = toUtcOrNow(text(event, "occurred_at"));

        Optional<AlertEvent> existing = alertRepository.findByPagerDutyIncidentId(incidentId);
        if (existing.isEmpty()) {
            if ("incident.triggered".equals(type)) {
                createFromWebhook(incidentId, data, occurredAt);
                return "Created";
            }
            // We missed the trigger; read the whole incident instead of guessing
            refreshIncident(incidentId);
            return "Fetched";
        }

        AlertEvent alert = existing.get();
        switch (type) {
            case "incident.acknowledged" -> {
                alert.setStatus(AlertEvent.STATUS_ACKNOWLEDGED);
                if (alert.getAcknowledgedAt() == null) {
                    alert.setAcknowledgedAt(notBefore(occurredAt, alert.getTriggeredAt()));
                }
            }
            case "incident.resolved" -> {
                alert.setStatus(AlertEvent.STATUS_RESOLVED);
                alert.setResolvedAt(notBefore(occurredAt, alert.getTriggeredAt()));
            }
            case "incident.escalated", "incident.reassigned" -> {
                Kind kind = type.endsWith("escalated") ? Kind.ESCALATED : Kind.REASSIGNED;
                for (JsonNode assignee : data.path("assignees")) {
                    recordWebhookAssignment(alert, text(assignee, "id"), kind, occurredAt);
                }
                String status = text(data, "status");
                if (status != null) alert.setStatus(status);
            }
            default -> {
                String status = text(data, "status");
                if (status != null) alert.setStatus(status);
            }
        }
        if (alert.getEngineer() == null) linkEngineer(alert);
        alertRepository.save(alert);
        return "Updated";
    }

    private void createFromWebhook(String incidentId, JsonNode data, LocalDateTime occurredAt) {
        AlertEvent alert = new AlertEvent();
        alert.setPagerDutyIncidentId(incidentId);
        alert.setSource("WEBHOOK");
        String status = text(data, "status");
        if (status != null) alert.setStatus(status);
        alert.setTitle(truncate(text(data, "title")));
        JsonNode number = data.has("number") ? data.path("number") : data.path("incident_number");
        if (number.canConvertToInt()) alert.setIncidentNumber(number.asInt());
        alert.setUrgency(text(data, "urgency"));
        alert.setSeverity(severity(text(data.path("priority"), "summary"), alert.getUrgency()));
        String createdAt = text(data, "created_at");
        alert.setTriggeredAt(createdAt != null ? toUtc(createdAt) : occurredAt);
        if (data.path("service").isObject()) {
            ServiceInfo service = new ServiceInfo();
            service.setId(text(data.path("service"), "id"));
            service.setName(text(data.path("service"), "summary"));
            alert.setService(resolveService(service));
        }

        JsonNode first = data.path("assignees").path(0);
        Assignee assignee = first.isObject() ? assignee(text(first, "id"), text(first, "summary")) : null;
        attribute(alert, assignee, incidentId, null);
        recordProvisionalPage(alertRepository.save(alert));
    }

    // ---------------------------------------------------------------------------------------------------
    // Attribution and paging history
    // ---------------------------------------------------------------------------------------------------

    private void attribute(AlertEvent alert, Assignee paged, String incidentId, LastStatusChangeBy lastChange) {
        // Last resort, only if a person (not a service or integration) changed the status
        if (paged == null && lastChange != null && "user_reference".equals(lastChange.getType())) {
            paged = assignee(lastChange.getId(), lastChange.getSummary());
        }
        if (paged == null || paged.getId() == null) {
            log.warn("Could not determine who was paged for incident {}", incidentId);
            return;
        }
        alert.setPagerDutyUserId(paged.getId());
        alert.setAssignedEngineerName(truncate(paged.getSummary()));
        linkEngineer(alert);
        if (alert.getEngineer() == null) {
            log.warn("No engineer with PagerDuty user id {} (incident {}); alert stored unattributed",
                    paged.getId(), incidentId);
        }
    }

    private void linkEngineer(AlertEvent alert) {
        if (alert.getPagerDutyUserId() != null) {
            engineerRepository.findByPagerDutyUserId(alert.getPagerDutyUserId()).ifPresent(alert::setEngineer);
        }
    }

    /** Rebuilds the alert's paging history from the incident log (the source of truth). */
    private void replaceAssignments(AlertEvent alert, List<LogEntry> incidentLog) {
        List<AlertAssignment> rows = assignmentsFromLog(alert, incidentLog);
        if (rows.isEmpty()) {
            // Nothing usable in the log (e.g. trimmed); keep what we have
            if (assignmentRepository.findByAlert_IdOrderByAssignedAtAsc(alert.getId()).isEmpty()) {
                recordProvisionalPage(alert);
            }
            return;
        }
        assignmentRepository.deleteByAlertId(alert.getId());
        assignmentRepository.saveAll(rows);
    }

    List<AlertAssignment> assignmentsFromLog(AlertEvent alert, List<LogEntry> incidentLog) {
        // Oldest first; at equal times prefer the escalation entry so an escalation isn't also a reassignment
        List<LogEntry> ordered = incidentLog.stream()
                .filter(e -> e.getCreatedAt() != null)
                .sorted(Comparator.comparing(LogEntry::getCreatedAt)
                        .thenComparing(e -> "escalate_log_entry".equals(e.getType()) ? 0 : 1))
                .toList();

        List<AlertAssignment> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, Optional<Engineer>> engineers = new HashMap<>();
        boolean paged = false;

        for (LogEntry entry : ordered) {
            Kind kind;
            List<Assignee> users = new ArrayList<>();
            if ("assign_log_entry".equals(entry.getType())) {
                kind = paged ? Kind.REASSIGNED : Kind.PAGED;
                if (entry.getAssignees() != null) users.addAll(entry.getAssignees());
            } else if ("escalate_log_entry".equals(entry.getType())) {
                kind = Kind.ESCALATED;
                if (entry.getAssignees() != null) users.addAll(entry.getAssignees());
                if (entry.getAssignedUser() != null) users.add(entry.getAssignedUser());
            } else {
                continue;
            }

            LocalDateTime at = notBefore(toUtc(entry.getCreatedAt()), alert.getTriggeredAt());
            for (Assignee user : users) {
                if (user.getId() == null || !seen.add(user.getId() + "@" + entry.getCreatedAt())) continue;
                Engineer engineer = engineers.computeIfAbsent(user.getId(), engineerRepository::findByPagerDutyUserId)
                        .orElse(null);
                rows.add(new AlertAssignment(alert, engineer, user.getId(), kind, at, AlertAssignment.Source.LOG));
                paged = true;
            }
        }
        return rows;
    }

    private void recordProvisionalPage(AlertEvent alert) {
        if (alert.getEngineer() == null && alert.getPagerDutyUserId() == null) return;
        assignmentRepository.save(new AlertAssignment(alert, alert.getEngineer(), alert.getPagerDutyUserId(),
                Kind.PAGED, alert.getTriggeredAt(), AlertAssignment.Source.WEBHOOK));
    }

    private void recordWebhookAssignment(AlertEvent alert, String userId, Kind kind, LocalDateTime at) {
        if (userId == null) return;
        LocalDateTime assignedAt = notBefore(at, alert.getTriggeredAt());
        if (assignmentRepository.existsByAlert_IdAndKindAndPagerdutyUserIdAndAssignedAt(
                alert.getId(), kind, userId, assignedAt)) {
            return;
        }
        Engineer engineer = engineerRepository.findByPagerDutyUserId(userId).orElse(null);
        assignmentRepository.save(new AlertAssignment(alert, engineer, userId, kind, assignedAt,
                AlertAssignment.Source.WEBHOOK));
    }

    private void applyAcknowledgement(AlertEvent alert, List<LogEntry> incidentLog) {
        if (alert.getAcknowledgedAt() != null) return;
        incidentLog.stream()
                .filter(e -> "acknowledge_log_entry".equals(e.getType()) && e.getCreatedAt() != null)
                .map(LogEntry::getCreatedAt)
                .min(Comparator.naturalOrder())
                .ifPresent(at -> alert.setAcknowledgedAt(notBefore(toUtc(at), alert.getTriggeredAt())));
    }

    private static Assignee firstPagedFromLog(List<LogEntry> incidentLog) {
        return incidentLog.stream()
                .filter(e -> "assign_log_entry".equals(e.getType()))
                .filter(e -> e.getAssignees() != null && !e.getAssignees().isEmpty())
                .min(Comparator.comparing(e -> e.getCreatedAt() == null ? "" : e.getCreatedAt()))
                .map(e -> e.getAssignees().getFirst())
                .orElse(null);
    }

    private List<LogEntry> fetchLog(String incidentId) {
        try {
            LogEntriesResponse response = webClient.get()
                    .uri(b -> b.path("/incidents/{id}/log_entries")
                            .queryParam("time_zone", "UTC")
                            .queryParam("limit", 100)
                            .build(incidentId))
                    .retrieve()
                    .bodyToMono(LogEntriesResponse.class)
                    .block();
            return response != null && response.getLogEntries() != null ? response.getLogEntries() : List.of();
        } catch (RuntimeException e) {
            log.warn("Could not read log entries for incident {}: {}", incidentId, e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // Services
    // ---------------------------------------------------------------------------------------------------

    private MonitoredService resolveService(ServiceInfo info) {
        if (info == null || info.getId() == null) return null;
        MonitoredService service = serviceRepository.findByPagerdutyServiceId(info.getId())
                .orElseGet(() -> {
                    MonitoredService created = new MonitoredService();
                    created.setPagerdutyServiceId(info.getId());
                    return created;
                });
        String name = truncate(info.getName() != null ? info.getName() : info.getId());
        if (!name.equals(service.getName())) {
            service.setName(name);
            service = serviceRepository.save(service);
        }
        return service;
    }

    // ---------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------

    private static Assignee firstAssignee(List<Assignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return null;
        return assignments.getFirst().getAssignee();
    }

    private static Assignee assignee(String id, String summary) {
        Assignee assignee = new Assignee();
        assignee.setId(id);
        assignee.setSummary(summary);
        return assignee;
    }

    /** PagerDuty priority when it is P1–P5, otherwise derived from urgency (high → P1, low → P3). */
    static String severity(String prioritySummary, String urgency) {
        if (prioritySummary != null && SEVERITY.matcher(prioritySummary).matches()) return prioritySummary;
        return "high".equalsIgnoreCase(urgency) ? "P1" : "P3";
    }

    static LocalDateTime toUtc(String isoTimestamp) {
        return OffsetDateTime.parse(isoTimestamp).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static LocalDateTime toUtcOrNow(String isoTimestamp) {
        return isoTimestamp != null ? toUtc(isoTimestamp) : LocalDateTime.now(ZoneOffset.UTC);
    }

    // The database rejects acknowledge/resolve/assign times before the trigger (clock skew between sources)
    private static LocalDateTime notBefore(LocalDateTime value, LocalDateTime floor) {
        return floor != null && value.isBefore(floor) ? floor : value;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asText() : null;
    }

    record Upsert(boolean created, boolean attributed) {
    }
}
