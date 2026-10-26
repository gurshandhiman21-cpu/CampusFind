package com.campusfind.dto;

import com.campusfind.entity.enums.ClaimStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for reviewing an ownership claim (approving or rejecting).
 */
public class ClaimReviewRequest {

    @NotNull(message = "Review status is required (APPROVED or REJECTED)")
    private ClaimStatus status;

    @Size(max = 500, message = "Review notes cannot exceed 500 characters")
    private String reviewNotes;

    public ClaimReviewRequest() {
    }

    public ClaimReviewRequest(ClaimStatus status, String reviewNotes) {
        this.status = status;
        this.reviewNotes = reviewNotes;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }
}
