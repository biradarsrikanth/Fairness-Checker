package com.example.fairnesstracker.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One report download, RUNNING while it's built and then SUCCEEDED or FAILED. Users, teams and engineers are
 * plain ids: the history outlives them (the foreign keys set them to null).
 */
@Entity
@Table(name = "report_export")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportExport {

    public enum Type { ANALYTICS, ENGINEER }

    public enum Format {
        PDF("application/pdf", "pdf"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

        private final String contentType;
        private final String extension;

        Format(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String contentType() {
            return contentType;
        }

        public String extension() {
            return extension;
        }
    }

    public enum Status { RUNNING, SUCCEEDED, FAILED }

    static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 16, updatable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private Format format;

    @Column(updatable = false)
    private Long teamId;

    @Column(updatable = false)
    private Long engineerId;

    @Column(nullable = false, updatable = false)
    private LocalDate windowStart;

    @Column(nullable = false, updatable = false)
    private LocalDate windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    private String fileName;

    private Long sizeBytes;

    private Integer durationMs;

    @Column(length = MAX_ERROR_LENGTH)
    private String error;

    // Filled by the database
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    ReportExport(Long userId, Type type, Format format, Long teamId, Long engineerId, LocalDate windowStart,
                 LocalDate windowEnd, String fileName) {
        this.userId = userId;
        this.type = type;
        this.format = format;
        this.teamId = teamId;
        this.engineerId = engineerId;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.fileName = fileName;
        this.status = Status.RUNNING;
    }

    void succeeded(long sizeBytes, int durationMs) {
        this.status = Status.SUCCEEDED;
        this.sizeBytes = sizeBytes;
        this.durationMs = durationMs;
    }

    void failed(String error, int durationMs) {
        this.status = Status.FAILED;
        this.durationMs = durationMs;
        this.error = error == null || error.length() <= MAX_ERROR_LENGTH ? error
                : error.substring(0, MAX_ERROR_LENGTH - 1) + "…";
    }
}
