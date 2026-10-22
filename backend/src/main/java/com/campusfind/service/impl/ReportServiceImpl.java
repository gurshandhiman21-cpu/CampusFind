package com.campusfind.service.impl;

import com.campusfind.dto.ReportCreateRequest;
import com.campusfind.dto.ReportResponse;
import com.campusfind.entity.Report;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.repository.ReportRepository;
import com.campusfind.service.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of ReportService providing business logic, validation,
 * server-side report ID generation, and entity-DTO transformations.
 */
@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;

    public ReportServiceImpl(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @Override
    @Transactional
    public ReportResponse createReport(ReportCreateRequest request) {
        // Generate safe, collision-free public identifier server-side
        String publicId = generatePublicReportId(request.getType());

        Report report = new Report();
        report.setReportId(publicId);
        report.setType(request.getType());
        report.setItemName(request.getItemName().trim());
        report.setCategory(request.getCategory().trim().toLowerCase());
        report.setDescription(request.getDescription().trim());
        report.setLocation(request.getLocation().trim().toLowerCase());
        report.setIncidentDate(request.getIncidentDate());
        report.setIncidentTime(request.getIncidentTime());
        report.setStatus(ReportStatus.OPEN);

        if (request.getAdditionalInfo() != null && !request.getAdditionalInfo().trim().isEmpty()) {
            report.setAdditionalInfo(request.getAdditionalInfo().trim());
        }

        if (request.getImageUrl() != null && !request.getImageUrl().trim().isEmpty()) {
            String cleanImageUrl = request.getImageUrl().trim();
            if (cleanImageUrl.startsWith("data:") || cleanImageUrl.startsWith("file:") || cleanImageUrl.contains("..") || cleanImageUrl.matches("^[a-zA-Z]:.*")) {
                throw new IllegalArgumentException("Invalid image URL. Base64 data, file URIs, and local filesystem paths are prohibited.");
            }
            report.setImageUrl(cleanImageUrl);
        }

        // Security requirement: Authentication is not implemented yet.
        // Never accept an unauthenticated userId from requests.
        report.setUser(null);

        Report saved = reportRepository.save(report);
        return ReportResponse.fromEntity(saved);
    }

    @Override
    public List<ReportResponse> getReports(ReportType type, String category, String location, ReportStatus status) {
        String cleanCategory = (category != null && !category.trim().isEmpty()) ? category.trim() : null;
        String cleanLocation = (location != null && !location.trim().isEmpty()) ? location.trim() : null;

        List<Report> reports = reportRepository.findWithFilters(type, cleanCategory, cleanLocation, status);
        return reports.stream()
                .map(ReportResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public ReportResponse getReportById(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new ResourceNotFoundException("Report identifier must not be empty");
        }

        String trimmedId = identifier.trim();

        // 1. Try resolving by public reportId (e.g. LST-20261016-A8F2)
        Optional<Report> reportOpt = reportRepository.findByReportId(trimmedId);

        // 2. Fallback: If not found and input is numeric, resolve by internal database primary key
        if (reportOpt.isEmpty()) {
            try {
                Long numericId = Long.parseLong(trimmedId);
                reportOpt = reportRepository.findById(numericId);
            } catch (NumberFormatException ignored) {
                // Not numeric, proceed to 404
            }
        }

        Report report = reportOpt.orElseThrow(() ->
                new ResourceNotFoundException("Report not found with identifier: " + trimmedId));

        return ReportResponse.fromEntity(report);
    }

    /**
     * Generates a safe, collision-free public identifier matching ${TYPE}-${YYYYMMDD}-${4_ALPHANUMERIC}.
     * Example: LST-20261016-A1B2 or FND-20261016-C3D4.
     */
    private String generatePublicReportId(ReportType type) {
        String prefix = (type == ReportType.LOST) ? "LST" : "FND";
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        String candidateId = prefix + "-" + datePart + "-" + randomSuffix;

        // Ensure collision safety
        while (reportRepository.existsByReportId(candidateId)) {
            randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
            candidateId = prefix + "-" + datePart + "-" + randomSuffix;
        }

        return candidateId;
    }
}
