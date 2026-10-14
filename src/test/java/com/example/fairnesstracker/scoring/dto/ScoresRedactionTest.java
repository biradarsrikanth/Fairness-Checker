package com.example.fairnesstracker.scoring.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** An engineer sees their team's fairness and time-of-day figures, but only their own row. */
class ScoresRedactionTest {

    @Test
    void fairnessKeepsTheTeamFiguresAndOnlyOneRow() {
        Scores.FairnessReport report = new Scores.FairnessReport(null, "SRE", "ONCALL_ROTATION", 0.31, "Fair",
                0.2, "HIGH", 12, List.of(load(1, "Asha"), load(2, "Ravi")), null);

        Scores.FairnessReport own = report.onlyEngineer(2);

        assertThat(own.gini()).isEqualTo(0.31);
        assertThat(own.label()).isEqualTo("Fair");
        assertThat(own.alertCount()).isEqualTo(12);
        assertThat(own.engineers()).extracting(Scores.EngineerLoad::name).containsExactly("Ravi");
    }

    @Test
    void timeOfDayKeepsOnlyOneRow() {
        Scores.TimeOfDayReport report = new Scores.TimeOfDayReport(null, "SRE", List.of(
                new Scores.EngineerTimeOfDay(1, "Asha", "SRE", 1, 2, 3, 4),
                new Scores.EngineerTimeOfDay(2, "Ravi", "SRE", 5, 6, 7, 8)));

        assertThat(report.onlyEngineer(1).engineers()).extracting(Scores.EngineerTimeOfDay::name)
                .containsExactly("Asha");
        assertThat(report.onlyEngineer(3).engineers()).isEmpty();
    }

    private static Scores.EngineerLoad load(long id, String name) {
        return new Scores.EngineerLoad(id, name, "SRE", 6, 0, 6.0, 50.0, null, null);
    }
}
