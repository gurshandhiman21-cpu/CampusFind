package com.campusfind.controller;

import com.campusfind.dto.RecoveryCreateRequest;
import com.campusfind.dto.RecoveryResponse;
import com.campusfind.service.RecoveryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing item recovery and physical handover endpoints under /api/reports/{reportId}/recovery.
 */
@RestController
@RequestMapping("/api/reports/{reportId}/recovery")
public class RecoveryController {

    private final RecoveryService recoveryService;

    public RecoveryController(RecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    /**
     * POST /api/reports/{reportId}/recovery
     * Records an item recovery and physical handover for an approved claim.
     */
    @PostMapping
    public ResponseEntity<RecoveryResponse> recordRecovery(
            @PathVariable String reportId,
            @Valid @RequestBody RecoveryCreateRequest request) {
        RecoveryResponse response = recoveryService.recordRecovery(reportId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/reports/{reportId}/recovery
     * Retrieves the recovery record for a resolved report. Only authorized for involved parties or admin/staff.
     */
    @GetMapping
    public ResponseEntity<RecoveryResponse> getRecovery(
            @PathVariable String reportId) {
        RecoveryResponse response = recoveryService.getRecoveryByReportId(reportId);
        return ResponseEntity.ok(response);
    }
}
