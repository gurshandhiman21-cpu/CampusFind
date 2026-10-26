package com.campusfind.dto;

import com.campusfind.entity.Claim;
import com.campusfind.entity.enums.ClaimStatus;
import com.campusfind.entity.enums.ReportType;

import java.time.LocalDateTime;

/**
 * Response DTO representing an ownership claim.
 * Confidential verification details are strictly masked unless explicitly authorized.
 */
public class ClaimResponse {

    private Long id;
    private Long reportId;
    private String reportPublicId;
    private String reportTitle;
    private ReportType reportType;
    private Long claimantId;
    private String claimantName;
    private String claimantEmail;
    private String explanation;
    private String verificationDetails;
    private ClaimStatus status;
    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ClaimResponse() {
    }

    public static ClaimResponse fromEntity(Claim claim, boolean includeConfidentialDetails) {
        if (claim == null) {
            return null;
        }

        ClaimResponse dto = new ClaimResponse();
        dto.setId(claim.getId());

        if (claim.getReport() != null) {
            dto.setReportId(claim.getReport().getId());
            dto.setReportPublicId(claim.getReport().getReportId());
            dto.setReportTitle(claim.getReport().getItemName());
            dto.setReportType(claim.getReport().getType());
        }

        if (claim.getClaimant() != null) {
            dto.setClaimantId(claim.getClaimant().getId());
            dto.setClaimantName(claim.getClaimant().getFullName());
            dto.setClaimantEmail(claim.getClaimant().getEmail());
        }

        dto.setExplanation(claim.getExplanation());
        dto.setVerificationDetails(includeConfidentialDetails ? claim.getVerificationDetails() : null);
        dto.setStatus(claim.getStatus());

        if (claim.getReviewedBy() != null) {
            dto.setReviewedById(claim.getReviewedBy().getId());
            dto.setReviewedByName(claim.getReviewedBy().getFullName());
        }

        dto.setReviewedAt(claim.getReviewedAt());
        dto.setReviewNotes(claim.getReviewNotes());
        dto.setCreatedAt(claim.getCreatedAt());
        dto.setUpdatedAt(claim.getUpdatedAt());

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

    public ReportType getReportType() {
        return reportType;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }

    public Long getClaimantId() {
        return claimantId;
    }

    public void setClaimantId(Long claimantId) {
        this.claimantId = claimantId;
    }

    public String getClaimantName() {
        return claimantName;
    }

    public void setClaimantName(String claimantName) {
        this.claimantName = claimantName;
    }

    public String getClaimantEmail() {
        return claimantEmail;
    }

    public void setClaimantEmail(String claimantEmail) {
        this.claimantEmail = claimantEmail;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getVerificationDetails() {
        return verificationDetails;
    }

    public void setVerificationDetails(String verificationDetails) {
        this.verificationDetails = verificationDetails;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public Long getReviewedById() {
        return reviewedById;
    }

    public void setReviewedById(Long reviewedById) {
        this.reviewedById = reviewedById;
    }

    public String getReviewedByName() {
        return reviewedByName;
    }

    public void setReviewedByName(String reviewedByName) {
        this.reviewedByName = reviewedByName;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
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
