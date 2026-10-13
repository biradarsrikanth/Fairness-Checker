package com.example.fairnesstracker.report;

/** A finished report, ready to download. */
public record ReportFile(String fileName, String contentType, byte[] content) {
}
