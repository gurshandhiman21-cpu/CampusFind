package com.campusfind.dto;

import com.campusfind.entity.Report;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Public response representation of an item report.
 * Strictly isolates internal JPA entities and excludes any sensitive claim verification data.
 */
public class ReportResponse {

    private Long id;
    private String reportId;
    private ReportType type;
    private String itemName;
    private String category;
    private String description;
    private String location;
    private LocalDate incidentDate;
    private LocalTime incidentTime;
    private String additionalInfo;
    private String imageUrl;
    private ReportStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ReportResponse() {
    }

    public static ReportResponse fromEntity(Report report) {
        if (report == null) {
            return null;
        }
        ReportResponse dto = new ReportResponse();
        dto.setId(report.getId());
        dto.setReportId(report.getReportId());
        dto.setType(report.getType());
        dto.setItemName(report.getItemName());
        dto.setCategory(report.getCategory());
        dto.setDescription(report.getDescription());
        dto.setLocation(report.getLocation());
        dto.setIncidentDate(report.getIncidentDate());
        dto.setIncidentTime(report.getIncidentTime());
        dto.setAdditionalInfo(report.getAdditionalInfo());
        dto.setImageUrl(report.getImageUrl());
        dto.setStatus(report.getStatus());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setUpdatedAt(report.getUpdatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public ReportType getType() {
        return type;
    }

    public void setType(ReportType type) {
        this.type = type;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public void setIncidentDate(LocalDate incidentDate) {
        this.incidentDate = incidentDate;
    }

    public LocalTime getIncidentTime() {
        return incidentTime;
    }

    public void setIncidentTime(LocalTime incidentTime) {
        this.incidentTime = incidentTime;
    }

    public String getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
