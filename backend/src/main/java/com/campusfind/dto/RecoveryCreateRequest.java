package com.campusfind.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for recording a verified physical item recovery and handover.
 */
public class RecoveryCreateRequest {

    private Long claimId;

    @NotNull(message = "Recovery date is required")
    @PastOrPresent(message = "Recovery date cannot be in the future")
    private LocalDate recoveryDate;

    @Size(max = 1000, message = "Handover notes cannot exceed 1000 characters")
    private String notes;

    public RecoveryCreateRequest() {
    }

    public RecoveryCreateRequest(Long claimId, LocalDate recoveryDate, String notes) {
        this.claimId = claimId;
        this.recoveryDate = recoveryDate;
        this.notes = notes;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
