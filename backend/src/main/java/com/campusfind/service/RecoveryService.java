package com.campusfind.service;

import com.campusfind.dto.RecoveryCreateRequest;
import com.campusfind.dto.RecoveryResponse;

/**
 * Service interface for recording verified physical item recovery and handovers.
 */
public interface RecoveryService {

    /**
     * Records an item recovery and physical handover.
     * Atomically creates an immutable RecoveryRecord, transitions the report to RECOVERED,
     * and auto-resolves any competing pending claims.
     *
     * @param reportPublicId public identifier of the report
     * @param request        recovery payload (claimId, recoveryDate, notes)
     * @return created recovery response
     */
    RecoveryResponse recordRecovery(String reportPublicId, RecoveryCreateRequest request);

    /**
     * Retrieves the recovery audit record for a resolved report.
     *
     * @param reportPublicId public identifier of the report
     * @return recovery response
     */
    RecoveryResponse getRecoveryByReportId(String reportPublicId);
}
