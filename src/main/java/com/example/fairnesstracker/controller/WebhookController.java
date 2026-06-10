package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.repository.AlertRepository;
import com.example.fairnesstracker.repository.EngineerRepository;
import com.example.fairnesstracker.security.HmacVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final ObjectMapper objectMapper;
    private final AlertRepository alertRepository;
    private final EngineerRepository engineerRepository;

    @Value("${pagerduty.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/pagerduty")
    public ResponseEntity<String> handleWebhook(
            @RequestBody(required = false) String rawPayload,
            @RequestHeader(value = "X-PagerDuty-Signature",
                    required = false) String signature) {

        try {

            if (rawPayload == null || rawPayload.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("Empty payload");
            }

            JsonNode root = objectMapper.readTree(rawPayload);

            String eventType = root.path("event")
                    .path("event_type")
                    .asText();

            if ("pagey.ping".equals(eventType)) {
                return ResponseEntity.ok("Ping received");
            }

            boolean valid = HmacVerifier.verify(
                    rawPayload,
                    signature,
                    webhookSecret);

            if (!valid) {
                return ResponseEntity.status(401)
                        .body("Invalid signature");
            }

            JsonNode incident = root.path("event")
                    .path("data");

            String incidentId = incident.path("id")
                    .asText();

            if ("incident.resolved".equals(eventType)) {

                alertRepository
                        .findByPagerDutyIncidentId(incidentId)
                        .ifPresent(alert -> {
                            alert.setStatus("resolved");
                            alert.setResolvedAt(LocalDateTime.now());
                            alertRepository.save(alert);
                        });

                return ResponseEntity.ok("Incident resolved");
            }

            if (alertRepository
                    .findByPagerDutyIncidentId(incidentId)
                    .isPresent()) {

                return ResponseEntity.ok("Already processed");
            }

            String pagerDutyUserId = incident
                    .path("assignees")
                    .get(0)
                    .path("id")
                    .asText();

            Engineer engineer = engineerRepository
                    .findByPagerDutyUserId(pagerDutyUserId)
                    .orElse(null);

            if (engineer == null) {
                return ResponseEntity.ok("Engineer mapping missing");
            }

            AlertEvent alert = new AlertEvent();

            alert.setPagerDutyIncidentId(
                    incident.path("id").asText());

            alert.setStatus(
                    incident.path("status").asText());

            alert.setSeverity(
                    incident.path("priority")
                            .path("summary")
                            .asText());

            alert.setTriggeredAt(
                    LocalDateTime.parse(
                            incident.path("created_at")
                                    .asText()
                                    .replace("Z", "")));

            alert.setEngineer(engineer);

            alertRepository.save(alert);

            return ResponseEntity.ok("Received");

        } catch (Exception e) {

            log.error("Webhook processing failed", e);

            return ResponseEntity.internalServerError()
                    .body("Webhook Error");
        }
    }
}