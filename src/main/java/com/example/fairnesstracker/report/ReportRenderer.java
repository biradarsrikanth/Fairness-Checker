package com.example.fairnesstracker.report;

/** Turns gathered report data into one file format. */
public interface ReportRenderer {

    ReportExport.Format format();

    byte[] render(ReportData data);
}
