package com.example.fairnesstracker.report;

import com.example.fairnesstracker.common.web.PageResponse;
import com.example.fairnesstracker.report.dto.ReportExportResponse;
import com.example.fairnesstracker.report.dto.ReportQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDF and Excel versions of the analytics pages, for what the caller may see. Each download is recorded
 * (GET /exports lists the caller's) and audited.
 */
@Tag(name = "Reports", description = "PDF and Excel reports of the analytics and engineer pages")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @Operation(operationId = "downloadAnalyticsReport", summary = "The analytics page as a PDF or Excel file",
            description = "Same filter as /analytics (team, start, end), at most REPORT_MAX_WINDOW_DAYS days (366 "
                    + "by default). The workbook has every alert and shift; the PDF summarises.",
            responses = @ApiResponse(responseCode = "200", description = "The file, as an attachment", content = {
                    @Content(mediaType = MediaType.APPLICATION_PDF_VALUE, schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary"))}))
    @GetMapping("/analytics")
    public ResponseEntity<byte[]> analytics(@Valid @ParameterObject ReportQuery query) {
        return download(reports.analytics(query.team(), query.start(), query.end(), query.fileFormat()));
    }

    @Operation(operationId = "downloadEngineerReport", summary = "One engineer's report as a PDF or Excel file",
            description = "Burnout and its parts, time of day, alerts and shifts. Engineers may download only their "
                    + "own; team is ignored.",
            responses = @ApiResponse(responseCode = "200", description = "The file, as an attachment", content = {
                    @Content(mediaType = MediaType.APPLICATION_PDF_VALUE, schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary"))}))
    @GetMapping("/engineers/{engineerId}")
    public ResponseEntity<byte[]> engineer(@PathVariable @Positive long engineerId,
                                           @Valid @ParameterObject ReportQuery query) {
        return download(reports.engineer(engineerId, query.start(), query.end(), query.fileFormat()));
    }

    @Operation(operationId = "listReportExports", summary = "My report downloads", description = "Newest first.")
    @GetMapping("/exports")
    public PageResponse<ReportExportResponse> exports(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.history(PageRequest.of(page, size));
    }

    private static ResponseEntity<byte[]> download(ReportFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                // File names are ASCII slugs, so the plain filename parameter is enough
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName()).build().toString())
                // Reports hold personal figures: never cached by the browser or a proxy
                .cacheControl(CacheControl.noStore())
                .contentLength(file.content().length)
                .body(file.content());
    }
}
