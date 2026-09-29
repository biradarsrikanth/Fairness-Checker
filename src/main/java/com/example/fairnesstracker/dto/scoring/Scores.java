package com.example.fairnesstracker.dto.scoring;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scorer v1 responses (fairneess-scorer src/schemas/scores.py). The scorer sends snake_case, which the
 * scorer WebClient maps onto these camelCase records; our API returns them as camelCase.
 * Unknown fields are ignored, so the scorer can add fields without breaking the gateway.
 */
public final class Scores {

    private Scores() {
    }

    public record Window(OffsetDateTime start, OffsetDateTime end, String timezone) {
    }

    public record EngineerRef(long engineerId, String name, String team) {
    }

    /** {@code oncallHours} and {@code loadPerOncallHour} are null when there is no shift data. */
    public record EngineerLoad(long engineerId, String name, String team, int alertCount,
                               int escalationsReceived, double load, double sharePct,
                               Double oncallHours, Double loadPerOncallHour) {
    }

    /** {@code basis}: ONCALL_ROTATION (compares people on call) or ACTIVE_ENGINEERS (no shift data). */
    public record FairnessReport(Window window, String team, String basis, double gini, String label,
                                 Double rotationGini, String confidence, int alertCount,
                                 List<EngineerLoad> engineers) {
    }

    public record BurnoutComponents(double exposure, double relativeLoad, double trend) {
    }

    public record BurnoutScore(long engineerId, String name, String team, int alertCount,
                               int escalationsReceived, double score, String risk, double nightPct,
                               double eveningPct, double weekendPct, double trendRatio,
                               Double medianAckMinutes, Double oncallHours, BurnoutComponents components) {
    }

    public record BurnoutReport(Window window, String team, List<BurnoutScore> engineers) {
    }

    public record TimeOfDayCounts(int night, int morning, int afternoon, int evening) {
    }

    public record EngineerTimeOfDay(long engineerId, String name, String team,
                                    int night, int morning, int afternoon, int evening) {
    }

    public record TimeOfDayReport(Window window, String team, List<EngineerTimeOfDay> engineers) {
    }

    public record EngineerReport(Window window, EngineerRef engineer, boolean active, BurnoutScore burnout,
                                 double teamSharePct, TimeOfDayCounts timeOfDay) {
    }
}
