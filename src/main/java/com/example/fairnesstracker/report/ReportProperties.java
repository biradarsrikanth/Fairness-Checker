package com.example.fairnesstracker.report;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits for PDF and Excel reports, which are built in memory in one request.
 *
 * @param maxWindowDays the longest window a report covers; at most 366, the on-call listing's limit
 * @param maxAlertRows  alerts listed in the Excel workbook (the PDF lists the latest few); the rest are counted
 * @param maxShiftRows  on-call shifts listed
 */
@Validated
@ConfigurationProperties("report")
public record ReportProperties(
        @Min(1) @Max(366) int maxWindowDays,
        @Min(1) @Max(100_000) int maxAlertRows,
        @Min(1) @Max(100_000) int maxShiftRows
) {
}
