package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.client.ScoringClient;
import com.example.fairnesstracker.dto.scoring.ScoreQuery;
import com.example.fairnesstracker.dto.scoring.Scores;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scores computed by the scorer service. Every endpoint accepts {@code days} or {@code start}/{@code end}
 * (ISO dates), and the team-level ones accept {@code team}.
 */
@RestController
@RequestMapping("/api/v1/scores")
public class ScoreController {

    private final ScoringClient scoringClient;

    public ScoreController(ScoringClient scoringClient) {
        this.scoringClient = scoringClient;
    }

    @GetMapping("/fairness")
    public Scores.FairnessReport fairness(@Valid ScoreQuery query) {
        return scoringClient.fairness(query);
    }

    @GetMapping("/burnout")
    public Scores.BurnoutReport burnout(@Valid ScoreQuery query) {
        return scoringClient.burnout(query);
    }

    @GetMapping("/timeofday")
    public Scores.TimeOfDayReport timeOfDay(@Valid ScoreQuery query) {
        return scoringClient.timeOfDay(query);
    }

    @GetMapping("/engineers/{engineerId}")
    public Scores.EngineerReport engineer(@PathVariable @Positive long engineerId, @Valid ScoreQuery query) {
        return scoringClient.engineer(engineerId, query);
    }
}
