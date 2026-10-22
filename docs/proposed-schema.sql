-- =============================================================================
-- CAMPUSFIND — PROPOSED DATABASE SCHEMA (PHASE 3)
-- Target RDBMS: MySQL 8.0+
-- Database Name: campusfind
--
-- STATUS: PROPOSED REFERENCE SCHEMA (NON-EXECUTED)
-- DO NOT EXECUTE DIRECTLY WITHOUT PRIOR REVIEW AND AUTHORIZATION.
--
-- Type Compatibility Note:
-- Primary keys and foreign keys use standard signed BIGINT to map directly
-- and cleanly to Java java.lang.Long (max value: 9.22 × 10^18), preventing
-- unsigned overflow discrepancies or Hibernate 6 dialect mapping frictions.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `campusfind`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `campusfind`;

-- -----------------------------------------------------------------------------
-- 1. Table: users
-- Supports future student, staff, and administrator authentication and auditing.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `full_name` VARCHAR(100) NOT NULL,
    `email` VARCHAR(150) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `role` VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_users_email` (`email`),
    CONSTRAINT `chk_users_role` CHECK (`role` IN ('STUDENT', 'ADMIN', 'STAFF')),
    CONSTRAINT `chk_users_status` CHECK (`status` IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. Table: reports
-- Supports lost and found reporting directly mapped from existing frontend forms.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `reports` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `report_id` VARCHAR(36) NOT NULL,
    `type` VARCHAR(10) NOT NULL,
    `item_name` VARCHAR(150) NOT NULL,
    `category` VARCHAR(50) NOT NULL,
    `description` TEXT NOT NULL,
    `location` VARCHAR(50) NOT NULL,
    `incident_date` DATE NOT NULL,
    `incident_time` TIME NULL,
    `additional_info` TEXT NULL,
    `image_url` VARCHAR(255) NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    `user_id` BIGINT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_reports_public_id` (`report_id`),
    KEY `idx_reports_type_status` (`type`, `status`),
    KEY `idx_reports_category` (`category`),
    KEY `idx_reports_location` (`location`),
    KEY `idx_reports_incident_date` (`incident_date` DESC),
    KEY `idx_reports_user_id` (`user_id`),
    CONSTRAINT `fk_reports_user` FOREIGN KEY (`user_id`)
        REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `chk_reports_type` CHECK (`type` IN ('LOST', 'FOUND')),
    CONSTRAINT `chk_reports_status` CHECK (`status` IN ('OPEN', 'CLAIM_PENDING', 'RECOVERED', 'CLOSED', 'DISPOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. Table: claims
-- Supports ownership claims submitted by students for found items.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `claims` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `report_id` BIGINT NOT NULL,
    `claimant_id` BIGINT NOT NULL,
    `explanation` TEXT NOT NULL,
    `verification_details` TEXT NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `reviewed_by` BIGINT NULL,
    `reviewed_at` TIMESTAMP NULL,
    `review_notes` VARCHAR(500) NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_claims_report_status` (`report_id`, `status`),
    KEY `idx_claims_claimant_id` (`claimant_id`),
    KEY `idx_claims_status` (`status`),
    KEY `idx_claims_reviewed_by` (`reviewed_by`),
    CONSTRAINT `fk_claims_report` FOREIGN KEY (`report_id`)
        REFERENCES `reports` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_claims_claimant` FOREIGN KEY (`claimant_id`)
        REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_claims_reviewer` FOREIGN KEY (`reviewed_by`)
        REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `chk_claims_status` CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 4. Table: recovery_records
-- Maintains an immutable audit log of verified item handovers.
-- UNIQUE(report_id) strictly prevents multiple handovers for the same item.
-- UNIQUE(claim_id) strictly prevents duplicate use of the same claim.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `recovery_records` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `report_id` BIGINT NOT NULL,
    `claim_id` BIGINT NULL,
    `recovery_date` DATE NOT NULL,
    `recorded_by` BIGINT NOT NULL,
    `notes` TEXT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_recoveries_report_id` (`report_id`),
    UNIQUE KEY `uq_recoveries_claim_id` (`claim_id`),
    KEY `idx_recoveries_recorded_by` (`recorded_by`),
    KEY `idx_recoveries_date` (`recovery_date` DESC),
    CONSTRAINT `fk_recoveries_report` FOREIGN KEY (`report_id`)
        REFERENCES `reports` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_recoveries_claim` FOREIGN KEY (`claim_id`)
        REFERENCES `claims` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_recoveries_admin` FOREIGN KEY (`recorded_by`)
        REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
