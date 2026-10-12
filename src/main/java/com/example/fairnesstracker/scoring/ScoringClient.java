package com.example.fairnesstracker.scoring;

import com.example.fairnesstracker.common.exception.ApiError;
import com.example.fairnesstracker.common.exception.ErrorCode;
import com.example.fairnesstracker.common.exception.ScoringServiceException;
import com.example.fairnesstracker.common.web.RequestIdFilter;
import com.example.fairnesstracker.scoring.dto.ScoreQuery;
import com.example.fairnesstracker.scoring.dto.Scores;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriBuilder;
import reactor.util.retry.Retry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Client for the scorer's /v1 API.
 * <ul>
 *   <li>Timeouts come from the WebClient (see HttpClientConfig).</li>
 *   <li>Connection failures and 502/503/504 are retried with exponential backoff; timeouts are not,
 *       because retrying a slow service only adds load to it.</li>
 *   <li>Scorer errors are translated into gateway errors: the scorer's documented client errors (404 unknown
 *       engineer, 400/422 invalid window) pass through; anything else means the gateway or its configuration
 *       is wrong (502 UPSTREAM_ERROR). Unreachable is 503, slow is 504, and a response that doesn't match the
 *       contract is a 502.</li>
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
        return get("/v1/scores/fairness", query::applyTo, Scores.FairnessReport.class);
    }

    public Scores.BurnoutReport burnout(ScoreQuery query) {
        return get("/v1/scores/burnout", query::applyTo, Scores.BurnoutReport.class);
    }

    public Scores.TimeOfDayReport timeOfDay(ScoreQuery query) {
        return get("/v1/scores/timeofday", query::applyTo, Scores.TimeOfDayReport.class);
    }

    public Scores.EngineerReport engineer(long engineerId, ScoreQuery query) {
        // An engineer is always scored within their own team
        return get("/v1/scores/engineers/{id}", query.withoutTeam()::applyTo, Scores.EngineerReport.class, engineerId);
    }

    /** Fairness and burnout per calendar week or month ({@code granularity}: "week" or "month"). */
    public Scores.TrendReport trend(ScoreQuery query, String granularity) {
        return get("/v1/scores/trend", builder -> query.applyTo(builder).queryParam("granularity", granularity),
                Scores.TrendReport.class);
    }

    private <T> T get(String path, UnaryOperator<UriBuilder> parameters, Class<T> type, Object... uriVariables) {
        // Read on the calling (servlet) thread; the MDC isn't available on Netty threads
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        return webClient.get()
                .uri(builder -> parameters.apply(builder.path(path)).build(uriVariables))
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
                .onErrorMap(DecodingException.class, ex -> fromInvalidResponse(path, ex))
                .block();
    }

    private ScoringServiceException fromUpstreamStatus(int upstream, String body) {
        UpstreamProblem problem = problem(body, upstream);
        String detail = problem.detail();
        return switch (upstream) {
            case 404 -> new ScoringServiceException(ErrorCode.NOT_FOUND, upstream, detail);
            case 400, 422 -> new ScoringServiceException(ErrorCode.VALIDATION_FAILED, upstream, detail,
                    problem.errors());
            case 401, 403 -> {
                log.error("Scorer rejected our API key; check SCORER_API_KEY on both services");
                yield new ScoringServiceException(ErrorCode.UPSTREAM_ERROR, upstream,
                        "Scoring service rejected the gateway's credentials");
            }
            case 503 -> new ScoringServiceException(ErrorCode.UPSTREAM_UNAVAILABLE, upstream,
                    "Scoring service is unavailable");
            case 504 -> new ScoringServiceException(ErrorCode.UPSTREAM_TIMEOUT, upstream, "Scoring service timed out");
            default -> {
                log.error("Scorer returned {}: {}", upstream, detail);
                yield new ScoringServiceException(ErrorCode.UPSTREAM_ERROR, upstream, "Scoring service error");
            }
        };
    }

    private static ScoringServiceException fromRequestFailure(WebClientRequestException ex) {
        if (isTimeout(ex)) {
            return new ScoringServiceException(ErrorCode.UPSTREAM_TIMEOUT, 0, "Scoring service timed out");
        }
        log.error("Scorer unreachable: {}", ex.getMessage());
        return new ScoringServiceException(ErrorCode.UPSTREAM_UNAVAILABLE, 0, "Scoring service is unavailable");
    }

    // The scorer answered 200 with JSON that doesn't match the contract (e.g. a required field missing)
    private static ScoringServiceException fromInvalidResponse(String path, DecodingException ex) {
        log.error("Scorer response for {} doesn't match the v1 contract: {}", path, ex.getMessage());
        return new ScoringServiceException(ErrorCode.UPSTREAM_ERROR, 200,
                "Scoring service returned an unexpected response");
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

    /** What the scorer said: its problem details' detail and field errors (RFC 9457, like ours). */
    private record UpstreamProblem(String detail, List<ApiError.FieldError> errors) {
    }

    // Falls back to the raw body (not JSON) or the status phrase (empty body)
    private UpstreamProblem problem(String body, int status) {
        try {
            JsonNode root = objectMapper.readTree(body);
            List<ApiError.FieldError> errors = new ArrayList<>();
            for (JsonNode error : root.path("errors")) {
                JsonNode field = error.path("field");
                errors.add(new ApiError.FieldError(field.isString() ? field.asString() : null,
                        truncate(error.path("message").asString(""))));
            }
            JsonNode detail = root.path("detail");
            if (detail.isString() && !detail.asString().isBlank()) {
                return new UpstreamProblem(truncate(detail.asString()), errors);
            }
        } catch (JacksonException ignored) {
            // not JSON
        }
        if (!body.isBlank()) return new UpstreamProblem(truncate(body), List.of());
        HttpStatus resolved = HttpStatus.resolve(status);
        return new UpstreamProblem(resolved != null ? resolved.getReasonPhrase() : "Error " + status, List.of());
    }

    private static String truncate(String s) {
        return s.length() <= MAX_DETAIL_LENGTH ? s : s.substring(0, MAX_DETAIL_LENGTH) + "…";
    }
}
