package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.dto.scoring.BurnoutResponse;
import com.example.fairnesstracker.dto.scoring.CombinedScoreResponse;
import com.example.fairnesstracker.dto.scoring.EngineerDetailResponse;
import com.example.fairnesstracker.dto.scoring.TimeOfDayCount;
import com.example.fairnesstracker.service.ScoringService;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CombinesScoreController {

    private final ScoringService scoringService;

    public CombinesScoreController(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    // Team fairness score
    @GetMapping("/combined-score")
    public CombinedScoreResponse combinedScore(
            @RequestParam(defaultValue = "365") int days) {

        return scoringService.getCombinedScore(days);
    }

    // Burnout scores
    @GetMapping("/burnout")
    public List<BurnoutResponse> burnout(
            @RequestParam(defaultValue = "365") int days) {

        return scoringService.getBurnout(days);
    }

    // Time-of-day heatmap
    @GetMapping("/timeofday")
    public Map<String, TimeOfDayCount> timeOfDay(
            @RequestParam(defaultValue = "365") int days) {

        return scoringService.getTimeOfDay(days);
    }

    // Individual engineer details
    @GetMapping("/engineer/{name}")
    public EngineerDetailResponse engineerDetail(
            @PathVariable String name,
            @RequestParam(defaultValue = "365") int days) {

        return scoringService.getEngineerDetail(name, days);
    }
}