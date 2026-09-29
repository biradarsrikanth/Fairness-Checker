package com.example.fairnesstracker.dto.scoring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.util.UriBuilder;

import java.time.LocalDate;

/**
 * Scoring window and scope, bound from query parameters: either {@code days} back from {@code end}
 * (default 365 days back from now) or an explicit {@code start}..{@code end} date range.
 */
public record ScoreQuery(
        @Min(1) @Max(3650) Integer days,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
        @Size(max = 255) String team
) {

    public UriBuilder applyTo(UriBuilder builder) {
        if (days != null) builder.queryParam("days", days);
        if (start != null) builder.queryParam("start", start);
        if (end != null) builder.queryParam("end", end);
        if (team != null && !team.isBlank()) builder.queryParam("team", team);
        return builder;
    }

    public ScoreQuery withoutTeam() {
        return new ScoreQuery(days, start, end, null);
    }
}
