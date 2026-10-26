package com.campusfind.repository;

import com.campusfind.entity.Claim;
import com.campusfind.entity.enums.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data JPA repository for Claim entity.
 */
@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    List<Claim> findByReportId(Long reportId);

    List<Claim> findByReportIdOrderByCreatedAtDesc(Long reportId);

    List<Claim> findByReportIdAndStatus(Long reportId, ClaimStatus status);

    List<Claim> findByReportIdAndStatusIn(Long reportId, Collection<ClaimStatus> statuses);

    List<Claim> findByClaimantId(Long claimantId);

    List<Claim> findByClaimantIdOrderByCreatedAtDesc(Long claimantId);

    boolean existsByReportIdAndClaimantIdAndStatusIn(Long reportId, Long claimantId, Collection<ClaimStatus> statuses);
}
