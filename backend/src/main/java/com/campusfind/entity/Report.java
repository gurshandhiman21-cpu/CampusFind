package com.campusfind.entity;

import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a lost or found item report.
 * Maps to table 'reports' in MySQL.
 */
@Entity
@Table(name = "reports", uniqueConstraints = {
    @UniqueConstraint(name = "uq_reports_public_id", columnNames = "report_id")
}, indexes = {
    @Index(name = "idx_reports_type_status", columnList = "type, status"),
    @Index(name = "idx_reports_category", columnList = "category"),
    @Index(name = "idx_reports_location", columnList = "location"),
    @Index(name = "idx_reports_incident_date", columnList = "incident_date"),
    @Index(name = "idx_reports_user_id", columnList = "user_id")
})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 36)
    @Column(name = "report_id", nullable = false, unique = true, length = 36)
    private String reportId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportType type;

    @NotBlank
    @Size(max = 150)
    @Column(name = "item_name", nullable = false, length = 150)
    private String itemName;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String category;

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String location;

    @NotNull
    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_time")
    private LocalTime incidentTime;

    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo;

    @Size(max = 255)
    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Non-destructive: CascadeType.REMOVE and orphanRemoval are omitted to safeguard claim history
    @OneToMany(mappedBy = "report")
    private List<Claim> claims = new ArrayList<>();

    // Non-destructive: CascadeType.REMOVE is omitted to preserve physical recovery records
    @OneToOne(mappedBy = "report", fetch = FetchType.LAZY)
    private RecoveryRecord recoveryRecord;

    public Report() {
    }

    public Report(String reportId, ReportType type, String itemName, String category,
                  String description, String location, LocalDate incidentDate) {
        this.reportId = reportId;
        this.type = type;
        this.itemName = itemName;
        this.category = category;
        this.description = description;
        this.location = location;
        this.incidentDate = incidentDate;
        this.status = ReportStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public ReportType getType() {
        return type;
    }

    public void setType(ReportType type) {
        this.type = type;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public void setIncidentDate(LocalDate incidentDate) {
        this.incidentDate = incidentDate;
    }

    public LocalTime getIncidentTime() {
        return incidentTime;
    }

    public void setIncidentTime(LocalTime incidentTime) {
        this.incidentTime = incidentTime;
    }

    public String getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Claim> getClaims() {
        return claims;
    }

    public void setClaims(List<Claim> claims) {
        this.claims = claims;
    }

    public RecoveryRecord getRecoveryRecord() {
        return recoveryRecord;
    }

    public void setRecoveryRecord(RecoveryRecord recoveryRecord) {
        this.recoveryRecord = recoveryRecord;
    }
}
