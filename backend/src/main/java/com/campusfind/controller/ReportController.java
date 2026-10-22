package com.campusfind.controller;

import com.campusfind.dto.ReportCreateRequest;
import com.campusfind.dto.ReportResponse;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;
import com.campusfind.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing lost and found report endpoints under /api/reports.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * POST /api/reports
     * Creates a new lost or found item report.
     */
    @PostMapping
    public ResponseEntity<ReportResponse> createReport(@Valid @RequestBody ReportCreateRequest request) {
        ReportResponse response = reportService.createReport(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/reports
     * Retrieves all reports with optional filtering by type, category, location, and status.
     */
    @GetMapping
    public ResponseEntity<List<ReportResponse>> getReports(
            @RequestParam(required = false) ReportType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) ReportStatus status) {
        List<ReportResponse> responses = reportService.getReports(type, category, location, status);
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/reports/{id}
     * Retrieves a single report by public reportId (e.g. LST-20261016-A8F2) or internal numeric ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> getReportById(@PathVariable String id) {
        ReportResponse response = reportService.getReportById(id);
        return ResponseEntity.ok(response);
    }
}
