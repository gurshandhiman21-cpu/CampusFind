package com.campusfind.controller;

import com.campusfind.dto.ClaimCreateRequest;
import com.campusfind.dto.ClaimResponse;
import com.campusfind.dto.ClaimReviewRequest;
import com.campusfind.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing ownership claim endpoints under /api/reports/{reportId}/claims and /api/claims.
 */
@RestController
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    /**
     * POST /api/reports/{reportId}/claims
     * Submits an ownership claim against a found item report.
     */
    @PostMapping("/api/reports/{reportId}/claims")
    public ResponseEntity<ClaimResponse> submitClaim(
            @PathVariable String reportId,
            @Valid @RequestBody ClaimCreateRequest request) {
        ClaimResponse response = claimService.submitClaim(reportId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/reports/{reportId}/claims
     * Retrieves all claims for a given report. Only authorized for the item finder or admin/staff.
     */
    @GetMapping("/api/reports/{reportId}/claims")
    public ResponseEntity<List<ClaimResponse>> getClaimsForReport(
            @PathVariable String reportId) {
        List<ClaimResponse> responses = claimService.getClaimsForReport(reportId);
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/claims/my
     * Retrieves all claims submitted by the currently authenticated user.
     */
    @GetMapping("/api/claims/my")
    public ResponseEntity<List<ClaimResponse>> getMyClaims() {
        List<ClaimResponse> responses = claimService.getMyClaims();
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/claims/{claimId}
     * Retrieves a single claim by its ID with role-based confidential details masking.
     */
    @GetMapping("/api/claims/{claimId}")
    public ResponseEntity<ClaimResponse> getClaimById(
            @PathVariable Long claimId) {
        ClaimResponse response = claimService.getClaimById(claimId);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/claims/{claimId}/review
     * Reviews (approves or rejects) a pending claim.
     */
    @PatchMapping("/api/claims/{claimId}/review")
    public ResponseEntity<ClaimResponse> reviewClaim(
            @PathVariable Long claimId,
            @Valid @RequestBody ClaimReviewRequest request) {
        ClaimResponse response = claimService.reviewClaim(claimId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/claims/{claimId}/withdraw
     * Allows the original claimant to withdraw their pending claim.
     */
    @PatchMapping("/api/claims/{claimId}/withdraw")
    public ResponseEntity<ClaimResponse> withdrawClaim(
            @PathVariable Long claimId) {
        ClaimResponse response = claimService.withdrawClaim(claimId);
        return ResponseEntity.ok(response);
    }
}
