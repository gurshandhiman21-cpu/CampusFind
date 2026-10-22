package com.campusfind.repository;

import com.campusfind.entity.RecoveryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for RecoveryRecord entity.
 */
@Repository
public interface RecoveryRecordRepository extends JpaRepository<RecoveryRecord, Long> {

    Optional<RecoveryRecord> findByReportId(Long reportId);

    boolean existsByReportId(Long reportId);

    Optional<RecoveryRecord> findByClaimId(Long claimId);
}
