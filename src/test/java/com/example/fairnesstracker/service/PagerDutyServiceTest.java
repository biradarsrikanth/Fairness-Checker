package com.example.fairnesstracker.service;

import com.example.fairnesstracker.dto.pagerDuty.SyncResult;
import com.example.fairnesstracker.entity.AlertAssignment;
import com.example.fairnesstracker.entity.AlertAssignment.Kind;
import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.entity.MonitoredService;
import com.example.fairnesstracker.entity.Team;
import com.example.fairnesstracker.repository.AlertAssignmentRepository;
import com.example.fairnesstracker.repository.AlertRepository;
import com.example.fairnesstracker.repository.EngineerRepository;
import com.example.fairnesstracker.repository.MonitoredServiceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagerDutyServiceTest {

    private static final String NO_LOG = "{\"log_entries\": []}";

    private MockWebServer pagerDuty;
    // PagerDuty responses by path prefix; anything else is a 404
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private AlertRepository alerts;
    private EngineerRepository engineers;
    private MonitoredServiceRepository services;
    private AlertAssignmentRepository assignments;
    private PagerDutyService service;
    private final Engineer asha = engineer(1L, "Asha", "PASHA");
    private final Engineer bala = engineer(2L, "Bala", "PBALA");

    @BeforeEach
    void setUp() throws IOException {
        pagerDuty = new MockWebServer();
        pagerDuty.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                return responses.entrySet().stream()
                        .filter(e -> path.startsWith(e.getKey()))
                        .findFirst()
                        .map(e -> json(e.getValue()))
                        .orElse(new MockResponse().setResponseCode(404));
            }
        });
        pagerDuty.start();

        alerts = mock(AlertRepository.class);
        engineers = mock(EngineerRepository.class);
        services = mock(MonitoredServiceRepository.class);
        assignments = mock(AlertAssignmentRepository.class);
        when(alerts.findByPagerDutyIncidentId(anyString())).thenReturn(Optional.empty());
        when(alerts.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 100L));
        when(services.findByPagerdutyServiceId(anyString())).thenReturn(Optional.empty());
        when(services.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(engineers.findByPagerDutyUserId(anyString())).thenReturn(Optional.empty());
        when(engineers.findByPagerDutyUserId("PASHA")).thenReturn(Optional.of(asha));
        when(engineers.findByPagerDutyUserId("PBALA")).thenReturn(Optional.of(bala));

        service = new PagerDutyService(WebClient.create(pagerDuty.url("/").toString()), alerts, engineers,
                services, assignments, TransactionOperations.withoutTransaction(), 30);
    }

    @AfterEach
    void tearDown() throws IOException {
        pagerDuty.shutdown();
    }

    @Test
    void creditsTheAssigneeNotWhoeverResolvedIt() {
        // Asha was paged; Bala resolved it
        responses.put("/incidents?", page(incident("Q1", assignedTo("PASHA", "Asha"), "PBALA")));
        responses.put("/incidents/Q1/log_entries", NO_LOG);

        SyncResult result = service.syncIncidents();

        AlertEvent saved = savedAlert();
        assertThat(saved.getEngineer()).isSameAs(asha);
        assertThat(saved.getPagerDutyUserId()).isEqualTo("PASHA");
        assertThat(saved.getTriggeredAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 18, 0));
        assertThat(result).isEqualTo(new SyncResult(1, 0, 0, 0));
    }

    @Test
    void storesTheServiceOnce() {
        responses.put("/incidents?", page(incident("Q1", assignedTo("PASHA", "Asha"), "PASHA")));
        responses.put("/incidents/Q1/log_entries", NO_LOG);

        service.syncIncidents();

        ArgumentCaptor<MonitoredService> captor = ArgumentCaptor.forClass(MonitoredService.class);
        verify(services).save(captor.capture());
        assertThat(captor.getValue().getPagerdutyServiceId()).isEqualTo("S1");
        assertThat(captor.getValue().getName()).isEqualTo("api");
        assertThat(savedAlert().getService()).isSameAs(captor.getValue());
    }

    @Test
    void buildsPagingHistoryAndAckTimeFromTheIncidentLog() {
        responses.put("/incidents?", page(incident("Q2", "[]", "PBALA")));
        responses.put("/incidents/Q2/log_entries", """
                {"log_entries": [
                  {"type": "resolve_log_entry", "created_at": "2026-09-27T19:00:00Z"},
                  {"type": "acknowledge_log_entry", "created_at": "2026-09-27T18:20:00Z",
                   "agent": {"id": "PBALA", "type": "user_reference"}},
                  {"type": "escalate_log_entry", "created_at": "2026-09-27T18:15:00Z",
                   "assignees": [{"id": "PBALA", "summary": "Bala"}]},
                  {"type": "assign_log_entry", "created_at": "2026-09-27T18:15:00Z",
                   "assignees": [{"id": "PBALA", "summary": "Bala"}]},
                  {"type": "assign_log_entry", "created_at": "2026-09-27T18:00:05Z",
                   "assignees": [{"id": "PASHA", "summary": "Asha"}]},
                  {"type": "assign_log_entry", "created_at": "2026-09-27T18:40:00Z",
                   "assignees": [{"id": "PGHOST", "summary": "Not one of ours"}]}
                ]}
                """);

        service.syncIncidents();

        AlertEvent saved = savedAlert();
        // Assignments were cleared on the incident, so the log decides who was paged
        assertThat(saved.getEngineer()).isSameAs(asha);
        assertThat(saved.getAcknowledgedAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 18, 20));

        verify(assignments).deleteByAlertId(100L);
        List<AlertAssignment> history = savedAssignments();
        assertThat(history).extracting(AlertAssignment::getKind, AlertAssignment::getPagerdutyUserId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Kind.PAGED, "PASHA"),
                        // Escalation and assignment at the same moment count once, as an escalation
                        org.assertj.core.groups.Tuple.tuple(Kind.ESCALATED, "PBALA"),
                        org.assertj.core.groups.Tuple.tuple(Kind.REASSIGNED, "PGHOST"));
        assertThat(history.get(2).getEngineer()).isNull();
        assertThat(history).allMatch(a -> a.getSource() == AlertAssignment.Source.LOG);
    }

    @Test
    void neverReattributesAnExistingAlertAndSkipsTheLogWhenNothingChanged() {
        AlertEvent existing = new AlertEvent();
        existing.setPagerDutyIncidentId("Q3");
        existing.setEngineer(asha);
        existing.setStatus("resolved");
        existing.setTriggeredAt(LocalDateTime.of(2026, 9, 27, 18, 0));
        when(alerts.findByPagerDutyIncidentId("Q3")).thenReturn(Optional.of(existing));
        responses.put("/incidents?", page(incident("Q3", assignedTo("PBALA", "Bala"), "PBALA")));

        SyncResult result = service.syncIncidents();

        assertThat(savedAlert().getEngineer()).isSameAs(asha);
        assertThat(result.updated()).isEqualTo(1);
        // Status unchanged: no log call, no history rewrite
        verify(assignments, never()).deleteByAlertId(any());
        assertThat(pagerDuty.getRequestCount()).isEqualTo(1);
    }

    @Test
    void unknownPriorityFallsBackToUrgency() {
        assertThat(PagerDutyService.severity("SEV-1", "high")).isEqualTo("P1");
        assertThat(PagerDutyService.severity(null, "low")).isEqualTo("P3");
        assertThat(PagerDutyService.severity("P2", "high")).isEqualTo("P2");
    }

    @Test
    void timestampsWithOffsetsAreStoredAsUtc() {
        assertThat(PagerDutyService.toUtc("2026-09-28T05:00:00+05:30")).isEqualTo(LocalDateTime.of(2026, 9, 27, 23, 30));
    }

    @Test
    void webhookStoresTriggeredIncidentForTheAssigneeWithProvisionalPage() throws Exception {
        String payload = """
                {"event": {"event_type": "incident.triggered", "occurred_at": "2026-09-27T18:00:01Z",
                  "data": {"id": "Q4", "type": "incident", "number": 42, "status": "triggered",
                           "title": "Disk full", "urgency": "high", "created_at": "2026-09-27T18:00:00Z",
                           "priority": null, "service": {"id": "S1", "summary": "api"},
                           "assignees": [{"id": "PBALA", "summary": "Bala"}]}}}
                """;

        String outcome = service.applyWebhookEvent(new ObjectMapper().readTree(payload).path("event"));

        assertThat(outcome).isEqualTo("Created");
        AlertEvent saved = savedAlert();
        assertThat(saved.getEngineer()).isSameAs(bala);
        assertThat(saved.getIncidentNumber()).isEqualTo(42);
        assertThat(saved.getSeverity()).isEqualTo("P1");
        assertThat(saved.getSource()).isEqualTo("WEBHOOK");
        AlertAssignment page = savedAssignment();
        assertThat(page.getKind()).isEqualTo(Kind.PAGED);
        assertThat(page.getSource()).isEqualTo(AlertAssignment.Source.WEBHOOK);
    }

    @Test
    void webhookAcknowledgementAndEscalationUpdateTheAlert() throws Exception {
        AlertEvent existing = new AlertEvent();
        withId(existing, 7L);
        existing.setPagerDutyIncidentId("Q5");
        existing.setEngineer(asha);
        existing.setTriggeredAt(LocalDateTime.of(2026, 9, 27, 18, 0));
        when(alerts.findByPagerDutyIncidentId("Q5")).thenReturn(Optional.of(existing));
        ObjectMapper mapper = new ObjectMapper();

        service.applyWebhookEvent(mapper.readTree("""
                {"event_type": "incident.acknowledged", "occurred_at": "2026-09-27T18:05:00Z",
                 "data": {"id": "Q5", "status": "acknowledged"}}
                """));
        service.applyWebhookEvent(mapper.readTree("""
                {"event_type": "incident.escalated", "occurred_at": "2026-09-27T18:30:00Z",
                 "data": {"id": "Q5", "status": "triggered", "assignees": [{"id": "PBALA"}]}}
                """));

        assertThat(existing.getAcknowledgedAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 18, 5));
        AlertAssignment escalation = savedAssignment();
        assertThat(escalation.getKind()).isEqualTo(Kind.ESCALATED);
        assertThat(escalation.getEngineer()).isSameAs(bala);
    }

    @Test
    void webhookForNonIncidentEventsIsIgnored() throws Exception {
        String outcome = service.applyWebhookEvent(new ObjectMapper().readTree(
                "{\"event_type\": \"service.updated\", \"data\": {\"id\": \"S1\"}}"));

        assertThat(outcome).isEqualTo("Ignored");
        verify(alerts, never()).save(any());
    }

    private AlertEvent savedAlert() {
        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alerts).save(captor.capture());
        return captor.getValue();
    }

    private AlertAssignment savedAssignment() {
        ArgumentCaptor<AlertAssignment> captor = ArgumentCaptor.forClass(AlertAssignment.class);
        verify(assignments).save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<AlertAssignment> savedAssignments() {
        ArgumentCaptor<List<AlertAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(assignments).saveAll(captor.capture());
        return captor.getValue();
    }

    private static String page(String incident) {
        return "{\"incidents\": [" + incident + "], \"more\": false}";
    }

    private static String assignedTo(String id, String name) {
        return "[{\"assignee\": {\"id\": \"%s\", \"summary\": \"%s\"}}]".formatted(id, name);
    }

    private static String incident(String id, String assignments, String lastChangedBy) {
        return """
                {"id": "%s", "status": "resolved", "title": "CPU high", "urgency": "high",
                 "incident_number": 7, "created_at": "2026-09-27T18:00:00Z",
                 "resolved_at": "2026-09-27T19:00:00Z", "priority": {"summary": "P2"},
                 "service": {"id": "S1", "summary": "api"}, "assignments": %s,
                 "last_status_change_by": {"id": "%s", "type": "user_reference", "summary": "someone"}}
                """.formatted(id, assignments, lastChangedBy);
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static AlertEvent withId(AlertEvent alert, Long id) {
        if (alert.getId() == null) ReflectionTestUtils.setField(alert, "id", id);
        return alert;
    }

    private static Engineer engineer(Long id, String name, String pagerDutyUserId) {
        Engineer engineer = new Engineer();
        ReflectionTestUtils.setField(engineer, "id", id);
        engineer.setName(name);
        engineer.setPagerDutyUserId(pagerDutyUserId);
        engineer.setTeam(new Team("SRE", "Asia/Kolkata"));
        engineer.setEmail(name.toLowerCase() + "@example.com");
        return engineer;
    }
}
