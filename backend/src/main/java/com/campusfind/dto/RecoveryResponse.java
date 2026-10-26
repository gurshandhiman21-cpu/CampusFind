package com.campusfind.dto;

import com.campusfind.entity.RecoveryRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO representing an immutable recovery handover audit log.
 */
public class RecoveryResponse {

    private Long id;
    private Long reportId;
    private String reportPublicId;
    private String reportTitle;
    private Long claimId;
    private LocalDate recoveryDate;
    private Long recordedById;
    private String recordedByName;
    private String notes;
    private LocalDateTime createdAt;

    public RecoveryResponse() {
    }

    public static RecoveryResponse fromEntity(RecoveryRecord record) {
        if (record == null) {
            return null;
        }

        RecoveryResponse dto = new RecoveryResponse();
        dto.setId(record.getId());

        if (record.getReport() != null) {
            dto.setReportId(record.getReport().getId());
            dto.setReportPublicId(record.getReport().getReportId());
            dto.setReportTitle(record.getReport().getItemName());
        }

        if (record.getClaim() != null) {
            dto.setClaimId(record.getClaim().getId());
        }

        dto.setRecoveryDate(record.getRecoveryDate());

        if (record.getRecordedBy() != null) {
            dto.setRecordedById(record.getRecordedBy().getId());
            dto.setRecordedByName(record.getRecordedBy().getFullName());
        }

        dto.setNotes(record.getNotes());
        dto.setCreatedAt(record.getCreatedAt());

        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public String getReportPublicId() {
        return reportPublicId;
    }

    public void setReportPublicId(String reportPublicId) {
        this.reportPublicId = reportPublicId;
    }

    public String getReportTitle() {
        return reportTitle;
    }

    public void setReportTitle(String reportTitle) {
        this.reportTitle = reportTitle;
    }

    public Long getClaimId() {
        return claimId;
    }

    public void setClaimId(Long claimId) {
        this.claimId = claimId;
    }

    public LocalDate getRecoveryDate() {
        return recoveryDate;
    }

    public void setRecoveryDate(LocalDate recoveryDate) {
        this.recoveryDate = recoveryDate;
    }

    public Long getRecordedById() {
        return recordedById;
    }

    public void setRecordedById(Long recordedById) {
        this.recordedById = recordedById;
    }

    public String getRecordedByName() {
        return recordedByName;
    }

    public void setRecordedByName(String recordedByName) {
        this.recordedByName = recordedByName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
