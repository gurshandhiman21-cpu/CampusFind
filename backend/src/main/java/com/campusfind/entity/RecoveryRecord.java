package com.campusfind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing an immutable chain-of-custody audit log for an item handover.
 * Maps to table 'recovery_records' in MySQL.
 * UNIQUE(report_id) strictly prevents multiple handovers for the same report.
 * UNIQUE(claim_id) strictly prevents duplicate use of the same approved claim.
 */
@Entity
@Table(name = "recovery_records", uniqueConstraints = {
    @UniqueConstraint(name = "uq_recoveries_report_id", columnNames = "report_id"),
    @UniqueConstraint(name = "uq_recoveries_claim_id", columnNames = "claim_id")
}, indexes = {
    @Index(name = "idx_recoveries_recorded_by", columnList = "recorded_by"),
    @Index(name = "idx_recoveries_date", columnList = "recovery_date")
})
public class RecoveryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, unique = true)
    private Report report;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", unique = true)
    private Claim claim;

    @NotNull
    @Column(name = "recovery_date", nullable = false)
    private LocalDate recoveryDate;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private User recordedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public RecoveryRecord() {
    }

    public RecoveryRecord(Report report, Claim claim, LocalDate recoveryDate, User recordedBy, String notes) {
        this.report = report;
        this.claim = claim;
        this.recoveryDate = recoveryDate;
        this.recordedBy = recordedBy;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public Claim getClaim() {
        return claim;
    }

    public void setClaim(Claim claim) {
        this.claim = claim;
    }

    public LocalDate getRecoveryDate() {
        return recoveryDate;
    }

    public void setRecoveryDate(LocalDate recoveryDate) {
        this.recoveryDate = recoveryDate;
    }

    public User getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(User recordedBy) {
        this.recordedBy = recordedBy;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
