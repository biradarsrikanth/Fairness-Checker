package com.example.fairnesstracker.service;

import com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyIncident;
import com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyResponse;
import com.example.fairnesstracker.dto.pagerDuty.user.PagerDutyUsersResponse;
import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.repository.AlertRepository;
import com.example.fairnesstracker.repository.EngineerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;


@Service
public class PagerDutyService {

    private final WebClient webClient;
    private final String apiToken;
    private final AlertRepository alertRepository;
    private final EngineerRepository engineerRepository;

    @Autowired
    public PagerDutyService(WebClient webClient,
                            @Value("${pagerduty.api-token}") String apiToken,
                            AlertRepository alertRepository,
                            EngineerRepository engineerRepository)
    {
        this.webClient = webClient;
        this.apiToken = apiToken;
        this.alertRepository = alertRepository;
        this.engineerRepository = engineerRepository;
    }

    public PagerDutyResponse getIncidents(){
       return webClient.get()
               .uri("/incidents")
               .header("Authorization", "Token token=" + apiToken)
               .header("Accept", "application/vnd.pagerduty+json;version=2")
               .retrieve()
               .bodyToMono(PagerDutyResponse.class)
               .block();
    }

    public PagerDutyUsersResponse getUsers() {

        return webClient.get()
                .uri("/users")
                .header("Authorization", "Token token=" + apiToken)
                .header("Accept", "application/vnd.pagerduty+json;version=2")
                .retrieve()
                .bodyToMono(PagerDutyUsersResponse.class)
                .block();
    }

    private AlertEvent mapToAlertEvent(PagerDutyIncident incident) {

        AlertEvent alert = new AlertEvent();

        // PagerDuty Incident ID
        alert.setPagerDutyIncidentId(
                incident.getId()
        );

        // Status
        alert.setStatus(
                incident.getStatus()
        );

        // Severity
        if (incident.getPriority() != null) {

            alert.setSeverity(
                    incident.getPriority().getSummary()
            );

        } else {

            if ("high".equalsIgnoreCase(
                    incident.getUrgency())) {

                alert.setSeverity("P1");

            } else {

                alert.setSeverity("P3");
            }
        }

        // Trigger Time
        alert.setTriggeredAt(
                LocalDateTime.parse(
                        incident.getCreatedAt()
                                .replace("Z", "")
                )
        );

        // Resolve Time
        if (incident.getResolvedAt() != null) {

            alert.setResolvedAt(
                    LocalDateTime.parse(
                            incident.getResolvedAt()
                                    .replace("Z", "")
                    )
            );
        }

        // Engineer Mapping
        if (incident.getAssignments() != null &&
                !incident.getAssignments().isEmpty()) {

            String engineerId =
                    incident.getAssignments()
                            .get(0)
                            .getAssignee()
                            .getId();

            Engineer engineer =
                    engineerRepository
                            .findByPagerDutyUserId(engineerId)
                            .orElse(null);

            alert.setEngineer(engineer);
        }

        return alert;
    }
}