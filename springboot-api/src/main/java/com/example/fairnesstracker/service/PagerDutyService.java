package com.example.fairnesstracker.service;

import com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyIncident;
import com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyResponse;
import com.example.fairnesstracker.dto.pagerDuty.user.PagerDutyUsersResponse;
import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.repository.AlertRepository;
import com.example.fairnesstracker.repository.EngineerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


@Service
public class PagerDutyService {

    private final WebClient webClient;
    private final String apiToken;
    private final AlertRepository alertRepository;
    private final EngineerRepository engineerRepository;
    private static final int DEFAULT_LIMIT = 25;

    @Autowired
    public PagerDutyService(WebClient webClient, @Value("${pagerduty.api-token}") String apiToken, AlertRepository alertRepository, EngineerRepository engineerRepository) {
        this.webClient = webClient;
        this.apiToken = apiToken;
        this.alertRepository = alertRepository;
        this.engineerRepository = engineerRepository;
    }

    public PagerDutyResponse getIncidents(int offset, int limit) {

        return webClient.get().uri(uriBuilder -> uriBuilder.path("/incidents").queryParam("offset", offset).queryParam("limit", limit).build()).retrieve().bodyToMono(PagerDutyResponse.class).block();
    }

    public PagerDutyResponse getIncidents() {
        return getIncidents(0, DEFAULT_LIMIT);
    }

    //helper for getAllIncidents
    private void processIncident(PagerDutyIncident incident) {

        Optional<AlertEvent> existing =
                alertRepository.findByPagerDutyIncidentId(
                        incident.getId());

        if (existing.isPresent()) {

            AlertEvent alert = existing.get();

            updateAlert(alert, incident);

            alertRepository.save(alert);

        } else {

            AlertEvent alert = mapToAlertEvent(incident);

            alertRepository.save(alert);
        }
    }

    public PagerDutyUsersResponse getUsers() {

        return webClient.get().uri("/users").header("Authorization", "Token token=" + apiToken).header("Accept", "application/vnd.pagerduty+json;version=2").retrieve().bodyToMono(PagerDutyUsersResponse.class).block();
    }

    private AlertEvent mapToAlertEvent(PagerDutyIncident incident) {

        AlertEvent alert = new AlertEvent();

        // PagerDuty Incident ID
        alert.setPagerDutyIncidentId(incident.getId());

        // Incident number (if present)
        if (incident.getIncidentNumber() != null) {
            alert.setIncidentNumber(incident.getIncidentNumber());
        }

        // Status
        alert.setStatus(incident.getStatus());

        // Title
        alert.setTitle(incident.getTitle());

        // Severity
        if (incident.getPriority() != null) {

            alert.setSeverity(incident.getPriority().getSummary());

        } else {
            if ("high".equalsIgnoreCase(incident.getUrgency())) {
                alert.setSeverity("P1");

            } else {
                alert.setSeverity("P3");
            }
        }
        // Trigger Time
        alert.setTriggeredAt(LocalDateTime.parse(incident.getCreatedAt().replace("Z", "")));

        // Resolve Time
        if (incident.getResolvedAt() != null) {
            alert.setResolvedAt(LocalDateTime.parse(incident.getResolvedAt().replace("Z", "")));
        }

        // Engineer Mapping
        if (incident.getLastStatusChangeBy() != null) {

            String pagerDutyUserId = incident.getLastStatusChangeBy().getId();

            alert.setPagerDutyUserId(pagerDutyUserId);

            engineerRepository.findByPagerDutyUserId(pagerDutyUserId)
                    .ifPresent(engineer -> {
                        alert.setEngineer(engineer);
                        alert.setAssignedEngineerName(engineer.getName());
                    });
        }

        // Assign first assignee name/id if present
        if (incident.getAssignments() != null && !incident.getAssignments().isEmpty()) {
            var a = incident.getAssignments().get(0).getAssignee();
            if (a != null) {
                alert.setAssignedEngineerName(a.getSummary());
                // keep pagerDutyUserId from last_status_change_by if present; otherwise use assignee id
                if (alert.getPagerDutyUserId() == null) {
                    alert.setPagerDutyUserId(a.getId());
                }
            }
        }

        // Urgency
        alert.setUrgency(incident.getUrgency());

        // Service info
        if (incident.getService() != null) {
            alert.setServiceId(incident.getService().getId());
            alert.setServiceName(incident.getService().getName());
        }

        // Source marking
        alert.setSource("API_SYNC");


        System.out.println("Incident ID: " + incident.getId());

        if (incident.getAssignments() == null) {
            System.out.println("Assignments = NULL");
        } else {
            System.out.println("Assignments count = " + incident.getAssignments().size());

            incident.getAssignments().forEach(a -> {
                if (a.getAssignee() != null) {
                    System.out.println("Assignee ID = " + a.getAssignee().getId());
                }
            });
        }
        System.out.println(incident.getAssignments());
        return alert;
    }

    public String syncIncidents() {

        int offset = 0;
        int limit = 25;

        boolean more;

        int totalSynced = 0;

        do {

            PagerDutyResponse response = getIncidents(offset, limit);

            for (PagerDutyIncident incident : response.getIncidents()) {

                processIncident(incident);

                totalSynced++;
            }

            more = response.isMore();

            offset += limit;

        } while (more);

        return "Sync Complete. Total incidents synced: " + totalSynced;
    }


    private void updateAlert(AlertEvent alert, PagerDutyIncident incident) {
        alert.setStatus(incident.getStatus());
        alert.setTitle(incident.getTitle());
        if (incident.getIncidentNumber() != null) {
            alert.setIncidentNumber(incident.getIncidentNumber());
        }
        if (incident.getPriority() != null) {
            alert.setSeverity(incident.getPriority().getSummary());
        }
        if (incident.getResolvedAt() != null) {
            alert.setResolvedAt(OffsetDateTime.parse(incident.getResolvedAt()).toLocalDateTime());
        }
        if (incident.getLastStatusChangeBy() != null) {

            String pagerDutyUserId = incident.getLastStatusChangeBy().getId();

            alert.setPagerDutyUserId(pagerDutyUserId);

            engineerRepository.findByPagerDutyUserId(pagerDutyUserId)
                    .ifPresent(engineer -> {
                        alert.setEngineer(engineer);
                        alert.setAssignedEngineerName(engineer.getName());
                    });
        }

        // update assignee/name/urgency/service if present
        if (incident.getAssignments() != null && !incident.getAssignments().isEmpty()) {
            var a = incident.getAssignments().get(0).getAssignee();
            if (a != null) {
                alert.setAssignedEngineerName(a.getSummary());
                if (alert.getPagerDutyUserId() == null) {
                    alert.setPagerDutyUserId(a.getId());
                }
            }
        }

        alert.setUrgency(incident.getUrgency());
        if (incident.getService() != null) {
            alert.setServiceId(incident.getService().getId());
            alert.setServiceName(incident.getService().getName());
        }
    }

    public void resolveIncident(String incidentId, String email) {

        Map<String, Object> incident = new HashMap<>();
        incident.put("id", incidentId);
        incident.put("type", "incident");
        incident.put("status", "resolved");

        Map<String, Object> body = new HashMap<>();
        body.put("incident", incident);

        webClient.put().uri("/incidents/{id}", incidentId).header("From", email).bodyValue(body).retrieve().onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class).map(error -> new RuntimeException("PagerDuty Error: " + error))).bodyToMono(String.class).block();

        syncIncidents();
    }


}
