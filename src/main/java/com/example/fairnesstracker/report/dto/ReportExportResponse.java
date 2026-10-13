package com.example.fairnesstracker.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "One report download. type: ANALYTICS or ENGINEER; format: PDF or XLSX; status: RUNNING, "
        + "SUCCEEDED or FAILED. team and engineer name what it covered (null: all teams, or since deleted)")
public record ReportExportResponse(long id, String type, String format, @Nullable String team,
                                   @Nullable Long engineerId, @Nullable String engineerName, LocalDate start,
                                   LocalDate end, String status, @Nullable String fileName,
                                   @Nullable Long sizeBytes, @Nullable Integer durationMs, @Nullable String error,
                                   Instant createdAt) {
}
