package com.example.fairnesstracker.alert.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Optional filters for the alert list, bound from query parameters; they combine with AND.
 * {@code start}/{@code end} are UTC dates, both inclusive, on the trigger time.
 */
public record AlertFilter(
        @Positive Long engineerId,
        @Size(max = 255) String team,
        @Pattern(regexp = "P1|P2|P3|P4|P5", message = "must be one of P1, P2, P3, P4, P5") String severity,
        @Pattern(regexp = "triggered|acknowledged|resolved",
                message = "must be one of triggered, acknowledged, resolved") String status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
) {
}
