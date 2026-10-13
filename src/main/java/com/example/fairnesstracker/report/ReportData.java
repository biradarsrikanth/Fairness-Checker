package com.example.fairnesstracker.report;

import com.example.fairnesstracker.alert.dto.AlertResponse;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Snapshot;
import com.example.fairnesstracker.oncall.dto.OncallShiftResponse;
import com.example.fairnesstracker.scoring.dto.Scores;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Everything a report shows, gathered once and rendered as PDF or Excel. */
public sealed interface ReportData {

    Header header();

    /**
     * What every report says about itself.
     *
     * @param scope       what it covers: a team's name, "All teams", or an engineer's name and team
     * @param generatedBy the email of who downloaded it
     * @param zone        the analytics timezone: dates are days there, times are shown there
     */
    record Header(String title, String scope, LocalDate start, LocalDate end, Instant generatedAt,
                  @Nullable String generatedBy, ZoneId zone) {
    }

    /** Up to the configured number of rows, and how many there were in all. */
    record Rows<T>(List<T> items, long total) {

        public boolean truncated() {
            return total > items.size();
        }
    }

    /**
     * The analytics page's figures. {@code timeOfDay} is null when the scorer couldn't answer (the snapshot
     * says why); on an engineer's own report it holds only their row.
     */
    record Analytics(Header header, Snapshot snapshot, Scores.@Nullable TimeOfDayReport timeOfDay,
                     Rows<AlertResponse> alerts, Rows<OncallShiftResponse> shifts) implements ReportData {
    }

    /** One engineer: their scores from the scorer, alerts and shifts. */
    record Engineer(Header header, Scores.EngineerReport report, Rows<AlertResponse> alerts,
                    Rows<OncallShiftResponse> shifts) implements ReportData {
    }
}
