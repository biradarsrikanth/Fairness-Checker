package com.example.fairnesstracker.scoring;

import io.swagger.v3.oas.annotations.Operation;
import com.example.fairnesstracker.security.AccessPolicy;
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

    private final AccessPolicy access;

    public ScoreController(ScoringClient scoringClient, AccessPolicy access) {
        this.scoringClient = scoringClient;
        this.access = access;
    }

    // Team-level: an engineer sees their team's figures but only their own row
    @Operation(description = "Admins may omit team (all teams); others get their only team, or must name one.")
    @GetMapping("/fairness")
    public Scores.FairnessReport fairness(@Valid @ParameterObject ScoreQuery query) {
        Scores.FairnessReport report = scoringClient.fairness(access.teamScoped(query, true));
        Long own = access.ownEngineerOnly();
        return own == null ? report : report.onlyEngineer(own);
    }

    // Peers' burnout is private: managers see their teams', engineers only their own (/engineers/{id})
    @Operation(description = "Admins and managers only. Admins may omit team; managers get their only team, "
            + "or must name one.")
    @GetMapping("/burnout")
    public Scores.BurnoutReport burnout(@Valid @ParameterObject ScoreQuery query) {
        return scoringClient.burnout(access.teamScoped(query, false));
    }

    @Operation(description = "Admins may omit team (all teams); others get their only team, or must name one.")
    @GetMapping("/timeofday")
    public Scores.TimeOfDayReport timeOfDay(@Valid @ParameterObject ScoreQuery query) {
        Scores.TimeOfDayReport report = scoringClient.timeOfDay(access.teamScoped(query, true));
        Long own = access.ownEngineerOnly();
        return own == null ? report : report.onlyEngineer(own);
    }

    @GetMapping("/engineers/{engineerId}")
    public Scores.EngineerReport engineer(@PathVariable @Positive long engineerId, @Valid @ParameterObject ScoreQuery query) {
        access.requireEngineer(engineerId);
        return scoringClient.engineer(engineerId, query);
    }
}
