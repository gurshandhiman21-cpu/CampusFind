package com.campusfind.repository;

import com.campusfind.entity.Report;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Report entity.
 */
@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByReportId(String reportId);

    boolean existsByReportId(String reportId);

    List<Report> findByType(ReportType type);

    List<Report> findByTypeAndStatus(ReportType type, ReportStatus status);

    List<Report> findByUserId(Long userId);

    /**
     * Flexible multi-criteria filter query supporting optional type, category, location, and status.
     * Results are sorted reverse-chronologically by incident date and creation timestamp.
     */
    @Query("SELECT r FROM Report r WHERE " +
           "(:type IS NULL OR r.type = :type) AND " +
           "(:category IS NULL OR LOWER(r.category) = LOWER(:category)) AND " +
           "(:location IS NULL OR LOWER(r.location) = LOWER(:location)) AND " +
           "(:status IS NULL OR r.status = :status) " +
           "ORDER BY r.incidentDate DESC, r.createdAt DESC")
    List<Report> findWithFilters(@Param("type") ReportType type,
                                 @Param("category") String category,
                                 @Param("location") String location,
                                 @Param("status") ReportStatus status);
}
