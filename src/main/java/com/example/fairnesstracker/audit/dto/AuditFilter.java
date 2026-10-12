package com.example.fairnesstracker.audit.dto;

import com.example.fairnesstracker.audit.AuditAction;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Optional filters, combined with AND; {@code start}/{@code end} are UTC dates, both inclusive. */
public record AuditFilter(
        AuditAction action,
        @Positive Long userId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
) {
}
