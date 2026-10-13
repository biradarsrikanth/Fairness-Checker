package com.example.fairnesstracker.report.dto;

import com.example.fairnesstracker.report.ReportExport;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Locale;

/**
 * What a report covers: like the analytics page, inclusive days in the analytics timezone (the last 30 by
 * default), and the file format.
 */
public record ReportQuery(
        @NotNull @Pattern(regexp = "pdf|xlsx", message = "must be pdf or xlsx") String format,
        @Size(max = 255) @Nullable String team,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate start,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate end
) {

    public ReportExport.Format fileFormat() {
        return ReportExport.Format.valueOf(format.toUpperCase(Locale.ROOT));
    }
}
