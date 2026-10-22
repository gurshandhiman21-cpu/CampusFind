package com.campusfind.entity.enums;

/**
 * Lifecycle status of an item report.
 * Compatible with chk_reports_status constraint: OPEN, CLAIM_PENDING, RECOVERED, CLOSED, DISPOSED.
 */
public enum ReportStatus {
    OPEN,
    CLAIM_PENDING,
    RECOVERED,
    CLOSED,
    DISPOSED
}
