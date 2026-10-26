package com.campusfind.service.impl;

import com.campusfind.dto.ClaimCreateRequest;
import com.campusfind.dto.ClaimResponse;
import com.campusfind.dto.ClaimReviewRequest;
import com.campusfind.entity.Claim;
import com.campusfind.entity.Report;
import com.campusfind.entity.User;
import com.campusfind.entity.enums.ClaimStatus;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;
import com.campusfind.entity.enums.UserRole;
import com.campusfind.exception.DuplicateClaimException;
import com.campusfind.exception.InvalidClaimStateException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.exception.UnauthorizedAccessException;
import com.campusfind.repository.ClaimRepository;
import com.campusfind.repository.ReportRepository;
import com.campusfind.repository.UserRepository;
import com.campusfind.service.ClaimService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of ClaimService enforcing ownership validation, anti-tampering,
 * duplicate claim prevention, confidential proof masking, and report state synchronization.
 */
@Service
@Transactional(readOnly = true)
public class ClaimServiceImpl implements ClaimService {

    private final ClaimRepository claimRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public ClaimServiceImpl(ClaimRepository claimRepository,
                            ReportRepository reportRepository,
                            UserRepository userRepository) {
        this.claimRepository = claimRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedAccessException("Authentication is required to perform this action.");
        }
        String email = auth.getName();
        if (email == null || email.trim().isEmpty()) {
            throw new UnauthorizedAccessException("Authentication is required to perform this action.");
        }
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UnauthorizedAccessException("Authenticated user account not found."));
    }

    private boolean isAdminOrStaff(User user) {
        return user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.STAFF;
    }

    private boolean canViewConfidentialDetails(Claim claim, User user) {
        if (user == null) {
            return false;
        }
        if (claim.getClaimant() != null && claim.getClaimant().getId().equals(user.getId())) {
            return true;
        }
        if (isAdminOrStaff(user)) {
            return true;
        }
        return claim.getReport() != null &&
                claim.getReport().getUser() != null &&
                claim.getReport().getUser().getId().equals(user.getId());
    }

    @Override
    @Transactional
    public ClaimResponse submitClaim(String reportPublicId, ClaimCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Claim request payload must not be null.");
        }

        User claimant = getCurrentUser();

        Report report = reportRepository.findByReportId(reportPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with ID: " + reportPublicId));

        if (report.getType() != ReportType.FOUND) {
            throw new InvalidClaimStateException("Claims can only be submitted for found items.");
        }

        if (report.getStatus() != ReportStatus.OPEN && report.getStatus() != ReportStatus.CLAIM_PENDING) {
            throw new InvalidClaimStateException("This item is not open for claims (current status: " + report.getStatus() + ").");
        }

        // Prevent finder from filing a claim on an item they reported finding
        if (report.getUser() != null && report.getUser().getId().equals(claimant.getId())) {
            throw new InvalidClaimStateException("You cannot submit an ownership claim on an item that you reported finding.");
        }

        // Prevent duplicate pending or approved claims from the same student on the same report
        boolean hasActiveClaim = claimRepository.existsByReportIdAndClaimantIdAndStatusIn(
                report.getId(),
                claimant.getId(),
                List.of(ClaimStatus.PENDING, ClaimStatus.APPROVED)
        );
        if (hasActiveClaim) {
            throw new DuplicateClaimException("You already have an active claim on this item.");
        }

        Claim claim = new Claim(
                report,
                claimant,
                request.getExplanation().trim(),
                request.getVerificationDetails().trim()
        );
        Claim savedClaim = claimRepository.save(claim);

        // Transition report status from OPEN to CLAIM_PENDING on first active claim
        if (report.getStatus() == ReportStatus.OPEN) {
            report.setStatus(ReportStatus.CLAIM_PENDING);
            reportRepository.save(report);
        }

        return ClaimResponse.fromEntity(savedClaim, true);
    }

    @Override
    public List<ClaimResponse> getClaimsForReport(String reportPublicId) {
        User currentUser = getCurrentUser();

        Report report = reportRepository.findByReportId(reportPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with ID: " + reportPublicId));

        boolean isFinder = report.getUser() != null && report.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = isAdminOrStaff(currentUser);

        if (!isFinder && !isAdmin) {
            throw new UnauthorizedAccessException("You are not authorized to view claims for this report.");
        }

        List<Claim> claims = claimRepository.findByReportIdOrderByCreatedAtDesc(report.getId());
        return claims.stream()
                .map(c -> ClaimResponse.fromEntity(c, canViewConfidentialDetails(c, currentUser)))
                .toList();
    }

    @Override
    public List<ClaimResponse> getMyClaims() {
        User currentUser = getCurrentUser();
        List<Claim> claims = claimRepository.findByClaimantIdOrderByCreatedAtDesc(currentUser.getId());
        return claims.stream()
                .map(c -> ClaimResponse.fromEntity(c, true))
                .toList();
    }

    @Override
    public ClaimResponse getClaimById(Long claimId) {
        User currentUser = getCurrentUser();

        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found with ID: " + claimId));

        boolean isClaimant = claim.getClaimant() != null && claim.getClaimant().getId().equals(currentUser.getId());
        boolean isFinder = claim.getReport() != null &&
                claim.getReport().getUser() != null &&
                claim.getReport().getUser().getId().equals(currentUser.getId());
        boolean isAdmin = isAdminOrStaff(currentUser);

        if (!isClaimant && !isFinder && !isAdmin) {
            throw new UnauthorizedAccessException("You are not authorized to view this claim.");
        }

        return ClaimResponse.fromEntity(claim, canViewConfidentialDetails(claim, currentUser));
    }

    @Override
    @Transactional
    public ClaimResponse reviewClaim(Long claimId, ClaimReviewRequest request) {
        if (request == null || request.getStatus() == null) {
            throw new IllegalArgumentException("Review decision is required.");
        }

        if (request.getStatus() != ClaimStatus.APPROVED && request.getStatus() != ClaimStatus.REJECTED) {
            throw new InvalidClaimStateException("Review decision must be either APPROVED or REJECTED.");
        }

        User reviewer = getCurrentUser();

        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found with ID: " + claimId));

        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new InvalidClaimStateException("Only claims in PENDING status can be reviewed (current status: " + claim.getStatus() + ").");
        }

        Report report = claim.getReport();
        boolean isFinder = report != null && report.getUser() != null && report.getUser().getId().equals(reviewer.getId());
        boolean isAdmin = isAdminOrStaff(reviewer);

        // Guest-reported items can only be adjudicated by administrative staff
        if (!isFinder && !isAdmin) {
            throw new UnauthorizedAccessException("You are not authorized to review claims for this report.");
        }

        claim.setStatus(request.getStatus());
        claim.setReviewedBy(reviewer);
        claim.setReviewedAt(LocalDateTime.now());
        if (request.getReviewNotes() != null && !request.getReviewNotes().trim().isEmpty()) {
            claim.setReviewNotes(request.getReviewNotes().trim());
        }

        Claim saved = claimRepository.save(claim);

        // If rejected, check if any pending or approved claims remain for this report
        if (request.getStatus() == ClaimStatus.REJECTED && report != null) {
            List<Claim> activeClaims = claimRepository.findByReportIdAndStatusIn(
                    report.getId(),
                    List.of(ClaimStatus.PENDING, ClaimStatus.APPROVED)
            );
            if (activeClaims.isEmpty() && report.getStatus() == ReportStatus.CLAIM_PENDING) {
                report.setStatus(ReportStatus.OPEN);
                reportRepository.save(report);
            }
        }

        return ClaimResponse.fromEntity(saved, true);
    }

    @Override
    @Transactional
    public ClaimResponse withdrawClaim(Long claimId) {
        User currentUser = getCurrentUser();

        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found with ID: " + claimId));

        if (claim.getClaimant() == null || !claim.getClaimant().getId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("You can only withdraw your own claims.");
        }

        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new InvalidClaimStateException("Only claims in PENDING status can be withdrawn (current status: " + claim.getStatus() + ").");
        }

        claim.setStatus(ClaimStatus.WITHDRAWN);
        Claim saved = claimRepository.save(claim);

        // Revert report status to OPEN if no other active claims exist
        Report report = claim.getReport();
        if (report != null && report.getStatus() == ReportStatus.CLAIM_PENDING) {
            List<Claim> activeClaims = claimRepository.findByReportIdAndStatusIn(
                    report.getId(),
                    List.of(ClaimStatus.PENDING, ClaimStatus.APPROVED)
            );
            if (activeClaims.isEmpty()) {
                report.setStatus(ReportStatus.OPEN);
                reportRepository.save(report);
            }
        }

        return ClaimResponse.fromEntity(saved, true);
    }
}
