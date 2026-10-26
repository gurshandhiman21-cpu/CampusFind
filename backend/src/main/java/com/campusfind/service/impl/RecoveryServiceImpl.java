package com.campusfind.service.impl;

import com.campusfind.dto.RecoveryCreateRequest;
import com.campusfind.dto.RecoveryResponse;
import com.campusfind.entity.Claim;
import com.campusfind.entity.RecoveryRecord;
import com.campusfind.entity.Report;
import com.campusfind.entity.User;
import com.campusfind.entity.enums.ClaimStatus;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.UserRole;
import com.campusfind.exception.InvalidClaimStateException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.exception.UnauthorizedAccessException;
import com.campusfind.repository.ClaimRepository;
import com.campusfind.repository.RecoveryRecordRepository;
import com.campusfind.repository.ReportRepository;
import com.campusfind.repository.UserRepository;
import com.campusfind.service.RecoveryService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of RecoveryService managing physical item handover transactions,
 * chain-of-custody audit logs, report resolution, and automated closure of competing claims.
 */
@Service
@Transactional(readOnly = true)
public class RecoveryServiceImpl implements RecoveryService {

    private final RecoveryRecordRepository recoveryRecordRepository;
    private final ReportRepository reportRepository;
    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;

    public RecoveryServiceImpl(RecoveryRecordRepository recoveryRecordRepository,
                               ReportRepository reportRepository,
                               ClaimRepository claimRepository,
                               UserRepository userRepository) {
        this.recoveryRecordRepository = recoveryRecordRepository;
        this.reportRepository = reportRepository;
        this.claimRepository = claimRepository;
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

    @Override
    @Transactional
    public RecoveryResponse recordRecovery(String reportPublicId, RecoveryCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Recovery request payload must not be null.");
        }

        User recorder = getCurrentUser();

        Report report = reportRepository.findByReportId(reportPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with ID: " + reportPublicId));

        if (report.getStatus() == ReportStatus.RECOVERED || recoveryRecordRepository.existsByReportId(report.getId())) {
            throw new InvalidClaimStateException("A recovery record already exists for this report.");
        }

        if (report.getStatus() == ReportStatus.CLOSED || report.getStatus() == ReportStatus.DISPOSED) {
            throw new InvalidClaimStateException("Cannot record recovery on a closed or disposed report.");
        }

        boolean isFinder = report.getUser() != null && report.getUser().getId().equals(recorder.getId());
        boolean isAdmin = isAdminOrStaff(recorder);

        if (!isFinder && !isAdmin) {
            throw new UnauthorizedAccessException("You are not authorized to record item recovery for this report.");
        }

        Claim claim = null;
        if (request.getClaimId() != null) {
            claim = claimRepository.findById(request.getClaimId())
                    .orElseThrow(() -> new ResourceNotFoundException("Claim not found with ID: " + request.getClaimId()));

            if (!claim.getReport().getId().equals(report.getId())) {
                throw new InvalidClaimStateException("The specified claim does not belong to this report.");
            }

            if (claim.getStatus() != ClaimStatus.APPROVED) {
                throw new InvalidClaimStateException("Cannot complete handover for a claim that has not been approved (current status: " + claim.getStatus() + ").");
            }

            if (recoveryRecordRepository.findByClaimId(claim.getId()).isPresent()) {
                throw new InvalidClaimStateException("This claim has already been associated with another recovery record.");
            }
        }

        RecoveryRecord record = new RecoveryRecord(
                report,
                claim,
                request.getRecoveryDate(),
                recorder,
                request.getNotes() != null && !request.getNotes().trim().isEmpty() ? request.getNotes().trim() : null
        );
        RecoveryRecord savedRecord = recoveryRecordRepository.save(record);

        // Transition report status to RECOVERED
        report.setStatus(ReportStatus.RECOVERED);
        reportRepository.save(report);

        // Automatically resolve any remaining PENDING claims on this report
        List<Claim> competingClaims = claimRepository.findByReportIdAndStatus(report.getId(), ClaimStatus.PENDING);
        for (Claim competing : competingClaims) {
            competing.setStatus(ClaimStatus.REJECTED);
            competing.setReviewedBy(recorder);
            competing.setReviewedAt(LocalDateTime.now());
            competing.setReviewNotes("Item physically recovered and handed over to verified owner.");
            claimRepository.save(competing);
        }

        return RecoveryResponse.fromEntity(savedRecord);
    }

    @Override
    public RecoveryResponse getRecoveryByReportId(String reportPublicId) {
        User currentUser = getCurrentUser();

        Report report = reportRepository.findByReportId(reportPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with ID: " + reportPublicId));

        RecoveryRecord record = recoveryRecordRepository.findByReportId(report.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No recovery record found for report: " + reportPublicId));

        boolean isFinder = report.getUser() != null && report.getUser().getId().equals(currentUser.getId());
        boolean isClaimant = record.getClaim() != null &&
                record.getClaim().getClaimant() != null &&
                record.getClaim().getClaimant().getId().equals(currentUser.getId());
        boolean isAdmin = isAdminOrStaff(currentUser);

        if (!isFinder && !isClaimant && !isAdmin) {
            throw new UnauthorizedAccessException("You are not authorized to view this recovery record.");
        }

        return RecoveryResponse.fromEntity(record);
    }
}
