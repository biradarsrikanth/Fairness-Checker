package com.example.fairnesstracker.controller;



import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CombinesScoreController {
    private final WebClient pythonScorerClient;

    public CombinesScoreController(
            @Qualifier("pythonScorerClient") WebClient pythonScorerClient
    ) {
        this.pythonScorerClient = pythonScorerClient;
    }

    @GetMapping("/combined-score")
    public Mono<Map> combinedScore() {

        return pythonScorerClient
                .get()
                .uri("/score")
                .retrieve()
                .bodyToMono(Map.class);
    }
}
