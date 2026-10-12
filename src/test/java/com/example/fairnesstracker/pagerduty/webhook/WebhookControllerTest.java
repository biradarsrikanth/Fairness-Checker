package com.example.fairnesstracker.pagerduty.webhook;

import com.example.fairnesstracker.alert.AlertEvent;
import com.example.fairnesstracker.alert.AlertRepository;
import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The full webhook path: raw body → JSON parsing → HMAC check → stored alert
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WebhookControllerTest {

    // pagerduty.webhook.secret in application-test.properties
    private static final String SECRET = "test-secret";

    private static final String TRIGGERED = """
            {"event": {"event_type": "incident.triggered", "occurred_at": "2026-09-27T23:30:00+05:30",
              "data": {"id": "QWEBHOOK1", "number": 77, "title": "Checkout latency", "status": "triggered",
                       "urgency": "high", "created_at": "2026-09-27T23:29:00+05:30",
                       "priority": {"summary": "P2"},
                       "service": {"id": "SWEBHOOK1", "summary": "Checkout"},
                       "assignees": [{"id": "PWEBHOOK1", "summary": "Bala"}]}}}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private TeamRepository teams;
    @Autowired private EngineerRepository engineers;
    @Autowired private AlertRepository alerts;

    @Test
    void storesSignedIncidentForTheEngineerWhoWasPaged() throws Exception {
        Engineer bala = new Engineer();
        bala.setName("Bala");
        bala.setEmail("bala.webhook@example.com");
        bala.setTeam(teams.save(new Team("Webhook SRE", "Asia/Kolkata")));
        bala.setPagerDutyUserId("PWEBHOOK1");
        bala = engineers.save(bala);

        send(TRIGGERED, sign(TRIGGERED))
                .andExpect(status().isOk())
                .andExpect(content().string("Created"));

        AlertEvent alert = alerts.findByPagerDutyIncidentId("QWEBHOOK1").orElseThrow();
        assertThat(alert.getEngineer().getId()).isEqualTo(bala.getId());
        assertThat(alert.getSeverity()).isEqualTo("P2");
        assertThat(alert.getIncidentNumber()).isEqualTo(77);
        assertThat(alert.getService().getName()).isEqualTo("Checkout");
        // Stored in UTC
        assertThat(alert.getTriggeredAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 17, 59));
        assertThat(alert.getSource()).isEqualTo("WEBHOOK");
    }

    @Test
    void rejectsPayloadThatDoesNotMatchTheSignature() throws Exception {
        String tampered = TRIGGERED.replace("P2", "P1");
        send(tampered, sign(TRIGGERED))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid signature"));
        assertThat(alerts.findByPagerDutyIncidentId("QWEBHOOK1")).isEmpty();
    }

    @Test
    void rejectsMissingSignature() throws Exception {
        send(TRIGGERED, null).andExpect(status().isUnauthorized());
    }

    @Test
    void answersPingWithoutSignature() throws Exception {
        send("{\"event\": {\"event_type\": \"pagey.ping\"}}", null)
                .andExpect(status().isOk())
                .andExpect(content().string("Ping received"));
    }

    @Test
    void rejectsEmptyAndMalformedBodies() throws Exception {
        send(" ", null).andExpect(status().isBadRequest()).andExpect(content().string("Empty payload"));
        send("{not json", null).andExpect(status().isBadRequest()).andExpect(content().string("Invalid JSON"));
    }

    @Test
    void ignoresSignedNonIncidentEvents() throws Exception {
        String payload = "{\"event\": {\"event_type\": \"service.updated\", \"data\": {\"id\": \"S1\"}}}";
        send(payload, sign(payload)).andExpect(status().isOk()).andExpect(content().string("Ignored"));
    }

    private ResultActions send(String payload, String signature) throws Exception {
        var request = post("/api/webhook/pagerduty").contentType(MediaType.APPLICATION_JSON).content(payload);
        if (signature != null) request.header("X-PagerDuty-Signature", signature);
        return mvc.perform(request);
    }

    private static String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "v1=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
