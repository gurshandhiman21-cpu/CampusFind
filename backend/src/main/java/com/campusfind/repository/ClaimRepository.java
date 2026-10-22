package com.campusfind.repository;

import com.campusfind.entity.Claim;
import com.campusfind.entity.enums.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for Claim entity.
 */
@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    List<Claim> findByReportId(Long reportId);

    List<Claim> findByReportIdAndStatus(Long reportId, ClaimStatus status);

    List<Claim> findByClaimantId(Long claimantId);
}
