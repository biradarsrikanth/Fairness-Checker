package com.example.fairnesstracker.client;

import com.example.fairnesstracker.config.ScorerProperties;
import com.example.fairnesstracker.dto.scoring.ScoreQuery;
import com.example.fairnesstracker.dto.scoring.Scores;
import com.example.fairnesstracker.exceptions.ScoringServiceException;
import com.example.fairnesstracker.web.RequestIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Set;

/**
 * Client for the scorer's /v1 API.
 * <ul>
 *   <li>Timeouts come from the WebClient (see HttpClientConfig).</li>
 *   <li>Connection failures and 502/503/504 are retried with exponential backoff; timeouts are not,
 *       because retrying a slow service only adds load to it.</li>
 *   <li>Scorer errors are translated into gateway statuses: 4xx pass through, auth failures mean our
 *       configuration is wrong (502), timeouts become 504 and other failures 502/503.</li>
 * </ul>
 */
@Slf4j
@Component
public class ScoringClient {

    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(502, 503, 504);
    private static final Duration FIRST_BACKOFF = Duration.ofMillis(200);
    private static final int MAX_DETAIL_LENGTH = 500;

    private final WebClient webClient;
    private final ScorerProperties props;
    private final ObjectMapper objectMapper;

    public ScoringClient(@Qualifier("scorerWebClient") WebClient webClient,
                         ScorerProperties props,
                         ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public Scores.FairnessReport fairness(ScoreQuery query) {
        return get("/v1/scores/fairness", query, Scores.FairnessReport.class);
    }

    public Scores.BurnoutReport burnout(ScoreQuery query) {
        return get("/v1/scores/burnout", query, Scores.BurnoutReport.class);
    }

    public Scores.TimeOfDayReport timeOfDay(ScoreQuery query) {
        return get("/v1/scores/timeofday", query, Scores.TimeOfDayReport.class);
    }

    public Scores.EngineerReport engineer(long engineerId, ScoreQuery query) {
        // An engineer is always scored within their own team
        return get("/v1/scores/engineers/{id}", query.withoutTeam(), Scores.EngineerReport.class, engineerId);
    }

    private <T> T get(String path, ScoreQuery query, Class<T> type, Object... uriVariables) {
        // Read on the calling (servlet) thread; the MDC isn't available on Netty threads
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        return webClient.get()
                .uri(builder -> query.applyTo(builder.path(path)).build(uriVariables))
                .headers(headers -> {
                    if (requestId != null) headers.set(RequestIdFilter.HEADER, requestId);
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> fromUpstreamStatus(response.statusCode().value(), body)))
                .bodyToMono(type)
                .retryWhen(Retry.backoff(props.maxRetries(), FIRST_BACKOFF)
                        .filter(ScoringClient::isRetryable)
                        .doBeforeRetry(signal -> log.warn("Retrying scorer call {} (retry {}): {}",
                                path, signal.totalRetries() + 1, signal.failure().toString()))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .onErrorMap(WebClientRequestException.class, ScoringClient::fromRequestFailure)
                .block();
    }

    private ScoringServiceException fromUpstreamStatus(int upstream, String body) {
        String detail = detail(body);
        if (upstream == 401 || upstream == 403) {
            log.error("Scorer rejected our API key; check SCORER_API_KEY on both services");
            return new ScoringServiceException(502, upstream, "Scoring service rejected the gateway's credentials");
        }
        if (upstream == 422) return new ScoringServiceException(400, upstream, detail);
        if (upstream < 500) return new ScoringServiceException(upstream, upstream, detail);
        if (upstream == 503) return new ScoringServiceException(503, upstream, "Scoring service is unavailable");
        if (upstream == 504) return new ScoringServiceException(504, upstream, "Scoring service timed out");
        log.error("Scorer returned {}: {}", upstream, detail);
        return new ScoringServiceException(502, upstream, "Scoring service error");
    }

    private static ScoringServiceException fromRequestFailure(WebClientRequestException ex) {
        if (isTimeout(ex)) {
            return new ScoringServiceException(504, 0, "Scoring service timed out");
        }
        log.error("Scorer unreachable: {}", ex.getMessage());
        return new ScoringServiceException(503, 0, "Scoring service is unavailable");
    }

    private static boolean isRetryable(Throwable ex) {
        if (ex instanceof ScoringServiceException s) {
            return RETRYABLE_STATUSES.contains(s.getUpstreamStatus());
        }
        return ex instanceof WebClientRequestException w && !isTimeout(w);
    }

    private static boolean isTimeout(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ReadTimeoutException || t instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    // FastAPI errors look like {"detail": "..."}; fall back to the raw body
    private String detail(String body) {
        try {
            JsonNode detail = objectMapper.readTree(body).path("detail");
            if (detail.isTextual()) return detail.asText();
            if (!detail.isMissingNode()) return truncate(detail.toString());
        } catch (Exception ignored) {
            // not JSON
        }
        return truncate(body);
    }

    private static String truncate(String s) {
        return s.length() <= MAX_DETAIL_LENGTH ? s : s.substring(0, MAX_DETAIL_LENGTH) + "…";
    }
}
