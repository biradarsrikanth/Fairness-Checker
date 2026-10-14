package com.example.fairnesstracker.scoring.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scorer v1 responses (fairneess-scorer src/schemas/scores.py). The scorer sends snake_case, which the
 * scorer WebClient maps onto these camelCase records; our API returns them as camelCase.
 * Unknown fields are ignored, so the scorer can add fields without breaking the gateway. A missing
 * primitive field is a contract violation (the call fails with 502) rather than a silent 0; fields the
 * scorer may omit are wrapper types such as {@code Double}.
 */
public final class Scores {

    private Scores() {
    }

    // Named for the spec: the analytics Window (dates) would otherwise take this schema's place
    @Schema(name = "ScoreWindow", description = "start: midnight of the first day in the timezone; end: exclusive, "
            + "midnight after the last day (or the current minute when no end was given)")
    public record Window(OffsetDateTime start, OffsetDateTime end, String timezone) {
    }

    public record EngineerRef(long engineerId, String name, String team) {
    }

    /** {@code oncallHours} and {@code loadPerOncallHour} are null when there is no shift data. */
    public record EngineerLoad(long engineerId, String name, String team, int alertCount,
                               int escalationsReceived, double load, double sharePct,
                               @Nullable Double oncallHours, @Nullable Double loadPerOncallHour) {
    }

    /** One team's own fairness, within a report covering several. */
    public record TeamFairness(String team, double gini, String label, @Nullable Double rotationGini,
                               int alertCount, int engineerCount) {
    }

    /**
     * {@code basis}: ONCALL_ROTATION (compares people on call) or ACTIVE_ENGINEERS (no shift data). For several
     * teams, {@code gini} is the average of the teams' own (listed in {@code teams}), weighted by alerts.
     */
    public record FairnessReport(Window window, @Nullable String team, String basis, double gini, String label,
                                 @Nullable Double rotationGini, String confidence, int alertCount,
                                 List<EngineerLoad> engineers, List<TeamFairness> teams) {

        public FairnessReport {
            // Scorers before the per-team breakdown don't send it
            teams = teams == null ? List.of() : List.copyOf(teams);
        }

        /** The team's figures, with only this engineer's row: peers' loads aren't shown to an engineer. */
        public FairnessReport onlyEngineer(long engineerId) {
            return new FairnessReport(window, team, basis, gini, label, rotationGini, confidence, alertCount,
                    engineers.stream().filter(e -> e.engineerId() == engineerId).toList(), teams);
        }
    }

    public record BurnoutComponents(double exposure, double relativeLoad, double trend) {
    }

    public record BurnoutScore(long engineerId, String name, String team, int alertCount,
                               int escalationsReceived, double score, String risk, double nightPct,
                               double eveningPct, double weekendPct, double trendRatio,
                               @Nullable Double medianAckMinutes, @Nullable Double oncallHours, BurnoutComponents components) {
    }

    public record BurnoutReport(Window window, @Nullable String team, List<BurnoutScore> engineers) {
    }

    public record TimeOfDayCounts(int night, int morning, int afternoon, int evening) {
    }

    public record EngineerTimeOfDay(long engineerId, String name, String team,
                                    int night, int morning, int afternoon, int evening) {
    }

    public record TimeOfDayReport(Window window, @Nullable String team, List<EngineerTimeOfDay> engineers) {

        public TimeOfDayReport onlyEngineer(long engineerId) {
            return new TimeOfDayReport(window, team,
                    engineers.stream().filter(e -> e.engineerId() == engineerId).toList());
        }
    }

    /** One calendar week or month; {@code end} is exclusive. {@code averageBurnout} is null when nobody is compared. */
    public record TrendPeriod(OffsetDateTime start, OffsetDateTime end, int alertCount, double gini, String label,
                              String confidence, @Nullable Double averageBurnout, int highRiskCount) {
    }

    public record TrendReport(Window window, @Nullable String team, String granularity, List<TrendPeriod> periods) {
    }

    public record EngineerReport(Window window, EngineerRef engineer, boolean active, BurnoutScore burnout,
                                 double teamSharePct, TimeOfDayCounts timeOfDay) {
    }
}
