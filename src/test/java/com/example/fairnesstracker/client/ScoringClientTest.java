package com.example.fairnesstracker.client;

import com.example.fairnesstracker.config.HttpClientConfig;
import com.example.fairnesstracker.config.ScorerProperties;
import com.example.fairnesstracker.dto.scoring.ScoreQuery;
import com.example.fairnesstracker.dto.scoring.Scores;
import com.example.fairnesstracker.exceptions.ScoringServiceException;
import com.example.fairnesstracker.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringClientTest {

    private static final String API_KEY = "test-api-key-0123456789abcdef0123456789";

    private MockWebServer server;
    private ScoringClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        ScorerProperties props = new ScorerProperties(server.url("/").toString(), API_KEY,
                Duration.ofSeconds(1), Duration.ofMillis(500), 2);
        WebClient webClient = new HttpClientConfig()
                .scorerWebClient(WebClient.builder(), props, Jackson2ObjectMapperBuilder.json());
        client = new ScoringClient(webClient, props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        MDC.clear();
    }

    @Test
    void sendsApiKeyRequestIdAndQueryAndMapsSnakeCase() throws Exception {
        server.enqueue(json(200, """
                {"window": {"start": "2026-09-01T00:00:00+05:30", "end": "2026-10-01T00:00:00+05:30",
                            "timezone": "Asia/Kolkata"},
                 "team": "SRE", "gini": 0.42, "label": "UNEQUAL", "confidence": "LOW", "alert_count": 7,
                 "engineers": [{"engineer_id": 1, "name": "Asha", "team": "SRE", "alert_count": 5,
                                "load": 20.5, "share_pct": 71.4, "added_later": true}]}
                """));
        MDC.put(RequestIdFilter.MDC_KEY, "req-123");

        Scores.FairnessReport report = client.fairness(
                new ScoreQuery(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "SRE"));

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath())
                .isEqualTo("/v1/scores/fairness?start=2026-09-01&end=2026-09-30&team=SRE");
        assertThat(request.getHeader("X-API-Key")).isEqualTo(API_KEY);
        assertThat(request.getHeader("X-Request-ID")).isEqualTo("req-123");
        assertThat(report.alertCount()).isEqualTo(7);
        assertThat(report.engineers().getFirst().sharePct()).isEqualTo(71.4);
        assertThat(report.window().timezone()).isEqualTo("Asia/Kolkata");
        assertThat(report.window().start().toString()).isEqualTo("2026-09-01T00:00+05:30");
    }

    @Test
    void retriesServiceUnavailableThenSucceeds() {
        server.enqueue(json(503, "{\"detail\": \"Database unavailable\"}"));
        server.enqueue(json(200, "{\"team\": null, \"engineers\": []}"));

        Scores.BurnoutReport report = client.burnout(new ScoreQuery(30, null, null, null));

        assertThat(report.engineers()).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void givesUpAfterMaxRetries() {
        for (int i = 0; i < 3; i++) server.enqueue(json(503, "{\"detail\": \"Database unavailable\"}"));

        assertThatThrownBy(() -> client.burnout(new ScoreQuery(30, null, null, null)))
                .isInstanceOfSatisfying(ScoringServiceException.class, e -> assertThat(e.getStatus()).isEqualTo(503));
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void passesNotFoundThroughWithoutRetrying() {
        server.enqueue(json(404, "{\"detail\": \"Engineer 9 not found\"}"));

        assertThatThrownBy(() -> client.engineer(9, new ScoreQuery(null, null, null, "ignored")))
                .isInstanceOfSatisfying(ScoringServiceException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(404);
                    assertThat(e.getMessage()).isEqualTo("Engineer 9 not found");
                });
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void rejectedApiKeyIsAGatewayError() {
        server.enqueue(json(401, "{\"detail\": \"Invalid or missing API key\"}"));

        assertThatThrownBy(() -> client.fairness(new ScoreQuery(null, null, null, null)))
                .isInstanceOfSatisfying(ScoringServiceException.class, e -> assertThat(e.getStatus()).isEqualTo(502));
    }

    @Test
    void slowScorerTimesOutWithoutRetrying() {
        server.enqueue(json(200, "{}").setHeadersDelay(2, TimeUnit.SECONDS));

        long started = System.nanoTime();
        assertThatThrownBy(() -> client.fairness(new ScoreQuery(null, null, null, null)))
                .isInstanceOfSatisfying(ScoringServiceException.class, e -> assertThat(e.getStatus()).isEqualTo(504));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1500));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void unreachableScorerIsServiceUnavailable() throws IOException {
        server.shutdown();

        assertThatThrownBy(() -> client.fairness(new ScoreQuery(null, null, null, null)))
                .isInstanceOfSatisfying(ScoringServiceException.class, e -> assertThat(e.getStatus()).isEqualTo(503));
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
