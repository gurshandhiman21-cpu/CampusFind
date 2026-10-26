package com.campusfind.service;

import com.campusfind.dto.ClaimCreateRequest;
import com.campusfind.dto.ClaimResponse;
import com.campusfind.dto.ClaimReviewRequest;

import java.util.List;

/**
 * Service interface for managing ownership claims on found items.
 */
public interface ClaimService {

    /**
     * Submits an ownership claim on a found item report.
     *
     * @param reportPublicId public identifier of the report (e.g. FND-20261026-B906)
     * @param request        claim details (explanation and confidential proof)
     * @return created claim response
     */
    ClaimResponse submitClaim(String reportPublicId, ClaimCreateRequest request);

    /**
     * Retrieves all claims submitted against a specific report.
     * Accessible only by the report finder or an administrator.
     *
     * @param reportPublicId public identifier of the report
     * @return list of claims
     */
    List<ClaimResponse> getClaimsForReport(String reportPublicId);

    /**
     * Retrieves all claims filed by the currently authenticated student.
     *
     * @return list of the student's claims
     */
    List<ClaimResponse> getMyClaims();

    /**
     * Retrieves a single claim by its identifier with role-based confidential proof masking.
     *
     * @param claimId internal claim ID
     * @return claim response
     */
    ClaimResponse getClaimById(Long claimId);

    /**
     * Reviews (approves or rejects) a pending claim.
     *
     * @param claimId internal claim ID
     * @param request review decision and notes
     * @return updated claim response
     */
    ClaimResponse reviewClaim(Long claimId, ClaimReviewRequest request);

    /**
     * Withdraws a pending claim previously submitted by the current user.
     *
     * @param claimId internal claim ID
     * @return updated claim response
     */
    ClaimResponse withdrawClaim(Long claimId);
}
