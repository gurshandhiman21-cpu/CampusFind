package com.campusfind.dto;

import com.campusfind.entity.enums.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request payload for creating a new lost or found item report.
 * Directly maps to existing frontend form fields.
 */
public class ReportCreateRequest {

    @NotNull(message = "Report type is required (LOST or FOUND)")
    private ReportType type;

    @NotBlank(message = "Item name is required")
    @Size(min = 2, max = 150, message = "Item name must be between 2 and 150 characters")
    private String itemName;

    @NotBlank(message = "Category is required")
    @Size(max = 50, message = "Category must not exceed 50 characters")
    private String category;

    @NotBlank(message = "Description is required")
    @Size(min = 5, max = 2000, message = "Description must be between 5 and 2000 characters")
    private String description;

    @NotBlank(message = "Location is required")
    @Size(max = 50, message = "Location must not exceed 50 characters")
    private String location;

    @NotNull(message = "Incident date is required")
    @PastOrPresent(message = "Incident date cannot be in the future")
    private LocalDate incidentDate;

    private LocalTime incidentTime;

    @Size(max = 2000, message = "Additional information must not exceed 2000 characters")
    private String additionalInfo;

    // Disallow Base64 data URLs to prevent database buffer bloat and memory exhaustion
    @Pattern(regexp = "^(?!data:).*$", message = "Base64 data URLs are not permitted. Provide a valid URL or path.")
    @Pattern(regexp = "^(?!file:).*$", message = "File URIs are not permitted. Provide a valid URL or path.")
    @Pattern(regexp = "^(?!.*\\.\\.).*$", message = "Path traversal is not permitted. Provide a valid URL or path.")
    @Size(max = 255, message = "Image URL must not exceed 255 characters")
    private String imageUrl;

    public ReportCreateRequest() {
    }

    public ReportCreateRequest(ReportType type, String itemName, String category,
                               String description, String location, LocalDate incidentDate) {
        this.type = type;
        this.itemName = itemName;
        this.category = category;
        this.description = description;
        this.location = location;
        this.incidentDate = incidentDate;
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
}
