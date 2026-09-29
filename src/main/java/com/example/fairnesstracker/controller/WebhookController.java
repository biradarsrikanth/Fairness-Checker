package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.security.HmacVerifier;
import com.example.fairnesstracker.service.PagerDutyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final ObjectMapper objectMapper;
    private final PagerDutyService pagerDutyService;

    @Value("${pagerduty.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/pagerduty")
    public ResponseEntity<String> handleWebhook(
            @RequestBody(required = false) String rawPayload,
            @RequestHeader(value = "X-PagerDuty-Signature", required = false) String signature) {

        if (rawPayload == null || rawPayload.isBlank()) {
            return ResponseEntity.badRequest().body("Empty payload");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body("Invalid JSON");
        }

        JsonNode event = root.path("event");
        if ("pagey.ping".equals(event.path("event_type").asText())) {
            return ResponseEntity.ok("Ping received");
        }

        if (!HmacVerifier.verify(rawPayload, signature, webhookSecret)) {
            log.warn("Rejected PagerDuty webhook with an invalid signature");
            return ResponseEntity.status(401).body("Invalid signature");
        }

        try {
            String outcome = pagerDutyService.applyWebhookEvent(event);
            log.info("PagerDuty webhook {} for incident {}: {}", event.path("event_type").asText(),
                    event.path("data").path("id").asText(), outcome);
            return ResponseEntity.ok(outcome);
        } catch (DataIntegrityViolationException e) {
            // The scheduled sync stored the same incident at the same moment; the unique index kept one copy
            return ResponseEntity.ok("Already processed");
        }
    }
}
