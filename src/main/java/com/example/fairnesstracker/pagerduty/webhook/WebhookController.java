package com.example.fairnesstracker.pagerduty.webhook;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Tag(name = "Webhooks", description = "PagerDuty webhook receiver (HMAC-signed)")
@RestController
// Not under /api/v1: PagerDuty is configured with this exact URL
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final ObjectMapper objectMapper;
    private final WebhookEventService webhookEvents;

    @Value("${pagerduty.webhook.secret}")
    private String webhookSecret;

    // PagerDuty signs its requests (X-PagerDuty-Signature) instead of sending a token
    @SecurityRequirements
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
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().body("Invalid JSON");
        }
        if (!root.isObject()) {
            return ResponseEntity.badRequest().body("Expected a JSON object");
        }

        JsonNode event = root.path("event");
        if ("pagey.ping".equals(event.path("event_type").asString())) {
            return ResponseEntity.ok("Ping received");
        }

        if (!HmacVerifier.verify(rawPayload, signature, webhookSecret)) {
            log.warn("Rejected PagerDuty webhook with an invalid signature");
            return ResponseEntity.status(401).body("Invalid signature");
        }

        // Stored once by event id; a failure answers 500, so PagerDuty retries and the retry applies it again
        return ResponseEntity.ok(webhookEvents.receive(rawPayload, root));
    }
}
