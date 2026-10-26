package com.campusfind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for submitting an ownership claim on a found item.
 */
public class ClaimCreateRequest {

    @NotBlank(message = "Explanation is required")
    @Size(min = 10, max = 2000, message = "Explanation must be between 10 and 2000 characters")
    private String explanation;

    @NotBlank(message = "Verification details are required")
    @Size(min = 5, max = 2000, message = "Verification details must be between 5 and 2000 characters")
    private String verificationDetails;

    public ClaimCreateRequest() {
    }

    public ClaimCreateRequest(String explanation, String verificationDetails) {
        this.explanation = explanation;
        this.verificationDetails = verificationDetails;
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
}
