package com.example.fairnesstracker.service;

import com.example.fairnesstracker.dto.scoring.BurnoutResponse;
import com.example.fairnesstracker.dto.scoring.CombinedScoreResponse;
import com.example.fairnesstracker.dto.scoring.EngineerDetailResponse;
import com.example.fairnesstracker.dto.scoring.TimeOfDayCount;
import com.example.fairnesstracker.exceptions.ScoringServiceException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class ScoringService {

    private final WebClient pythonScorerClient;

    public ScoringService(
            @Qualifier("pythonScorerClient") WebClient pythonScorerClient
    ) {
        this.pythonScorerClient = pythonScorerClient;
    }

    // Team fairness score
    public CombinedScoreResponse getCombinedScore(int days) {

        return pythonScorerClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/score")
                        .queryParam("days", days)
                        .build())
                .retrieve()
                .onStatus(
                        status -> status.value() >= 400 && status.value() < 500,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new ScoringServiceException(
                                        response.statusCode().value(),
                                        "Scoring request failed: " + body
                                ))
                )
                .onStatus(
                        status -> status.value() >= 500,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new ScoringServiceException(
                                        response.statusCode().value(),
                                        "Scoring service error: " + body
                                ))
                )
                .bodyToMono(CombinedScoreResponse.class)
                .block();
    }

    // Burnout scores
    public List<BurnoutResponse> getBurnout(int days) {

        return pythonScorerClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/score/burnout")
                        .queryParam("days", days)
                        .build())
                .retrieve()
                .onStatus(
                        status -> status.value() >= 400 && status.value() < 500,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new ScoringServiceException(
                                        response.statusCode().value(),
                                        "Scoring request failed: " + body
                                ))
                )
                .onStatus(
                        status -> status.value() >= 500,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new ScoringServiceException(
                                        response.statusCode().value(),
                                        "Scoring service error: " + body
                                ))
                )
                .bodyToMono(
                        new ParameterizedTypeReference<List<BurnoutResponse>>() {
                        }
                )
                .block();
    }

    // Time-of-day heatmap
    public Map<String, TimeOfDayCount> getTimeOfDay(int days) {

        return pythonScorerClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/score/timeofday")
                        .queryParam("days", days)
                        .build())
                .retrieve()
                .onStatus(
                        status -> status.value() >= 400 && status.value() < 500,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new ScoringServiceException(
                                        response.statusCode().value(),
                                        "Scoring request failed: " + body
                                ))
                )
                .onStatus(
                        status -> status.value() >= 500,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new ScoringServiceException(
                                        response.statusCode().value(),
                                        "Scoring service error: " + body
                                ))
                )
                .bodyToMono(
                        new ParameterizedTypeReference<
                                Map<String, TimeOfDayCount>>() {
                        }
                )
                .block();
    }

    // Individual engineer details
    public EngineerDetailResponse getEngineerDetail(
            String name,
            int days
    ) {

        return pythonScorerClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/score/engineer/{name}")
                        .queryParam("days", days)
                        .build(name))
                .retrieve()
                .onStatus(
                        status -> status.value() >= 400 && status.value() < 500,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new ScoringServiceException(
                                        response.statusCode().value(),
                                        "Scoring request failed: " + body
                                ))
                )
                .onStatus(
                        status -> status.value() >= 500,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new ScoringServiceException(
                                        response.statusCode().value(),
                                        "Scoring service error: " + body
                                ))
                )
                .bodyToMono(EngineerDetailResponse.class)
                .block();
    }
}