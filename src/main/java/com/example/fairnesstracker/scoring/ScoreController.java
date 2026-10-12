package com.example.fairnesstracker.scoring;

import com.example.fairnesstracker.scoring.dto.ScoreQuery;
import com.example.fairnesstracker.scoring.dto.Scores;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scores computed by the scorer service. Every endpoint accepts {@code days} or {@code start}/{@code end}
 * (ISO dates), and the team-level ones accept {@code team}.
 */
@Tag(name = "Scores", description = "Fairness, burnout and time-of-day scores from the scorer service")
@RestController
@RequestMapping("/api/v1/scores")
public class ScoreController {

    private final ScoringClient scoringClient;

    public ScoreController(ScoringClient scoringClient) {
        this.scoringClient = scoringClient;
    }

    @GetMapping("/fairness")
    public Scores.FairnessReport fairness(@Valid @ParameterObject ScoreQuery query) {
        return scoringClient.fairness(query);
    }

    @GetMapping("/burnout")
    public Scores.BurnoutReport burnout(@Valid @ParameterObject ScoreQuery query) {
        return scoringClient.burnout(query);
    }

    @GetMapping("/timeofday")
    public Scores.TimeOfDayReport timeOfDay(@Valid @ParameterObject ScoreQuery query) {
        return scoringClient.timeOfDay(query);
    }

    @GetMapping("/engineers/{engineerId}")
    public Scores.EngineerReport engineer(@PathVariable @Positive long engineerId, @Valid @ParameterObject ScoreQuery query) {
        return scoringClient.engineer(engineerId, query);
    }
}
