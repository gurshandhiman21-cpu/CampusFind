package com.campusfind.entity.enums;

/**
 * Status of an ownership claim submitted by a student.
 * Compatible with chk_claims_status constraint: PENDING, APPROVED, REJECTED, WITHDRAWN.
 */
public enum ClaimStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
