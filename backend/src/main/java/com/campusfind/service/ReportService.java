package com.campusfind.service;

import com.campusfind.dto.ReportCreateRequest;
import com.campusfind.dto.ReportResponse;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;

import java.util.List;

/**
 * Service contract for report operations.
 */
public interface ReportService {

    /**
     * Creates and persists a new item report.
     * Generates a safe, collision-free public identifier server-side.
     */
    ReportResponse createReport(ReportCreateRequest request);

    /**
     * Retrieves a list of reports matching optional filter parameters.
     */
    List<ReportResponse> getReports(ReportType type, String category, String location, ReportStatus status);

    /**
     * Retrieves a single report by public reportId (e.g. LST-20261016-A1B2) or internal numeric ID.
     */
    ReportResponse getReportById(String identifier);
}
