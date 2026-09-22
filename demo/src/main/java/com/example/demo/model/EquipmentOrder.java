package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "equipment_orders")
public class EquipmentOrder {

    public enum EquipmentType {
        CPAP, BIPAP, DME
    }

    public enum ReviewStatus {
        PENDING_REVIEW, CONFIRMED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private EquipmentType equipmentType;

    private String type;
    private String featureFeature;
    private String adjustFeature;
    private String range;
    private String company;
    private String cost;
    private String pressureSetting;

    // Staff-entered orders still go through review before being treated as
    // actionable, consistent with the OCR-derived order flow -- a manual
    // entry can still have typos or be entered against the wrong patient.
    @Enumerated(EnumType.STRING)
    private ReviewStatus status = ReviewStatus.PENDING_REVIEW;

    // NEW: fields for the order status/tracking page.
    private String supervision;
    private String followUp;
    @Column(columnDefinition = "TEXT")
    private String reviewNotes;

    private String createdBy;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    public EquipmentOrder() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EquipmentType getEquipmentType() { return equipmentType; }
    public void setEquipmentType(EquipmentType equipmentType) { this.equipmentType = equipmentType; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFeatureFeature() { return featureFeature; }
    public void setFeatureFeature(String featureFeature) { this.featureFeature = featureFeature; }

    public String getAdjustFeature() { return adjustFeature; }
    public void setAdjustFeature(String adjustFeature) { this.adjustFeature = adjustFeature; }

    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getCost() { return cost; }
    public void setCost(String cost) { this.cost = cost; }

    public String getPressureSetting() { return pressureSetting; }
    public void setPressureSetting(String pressureSetting) { this.pressureSetting = pressureSetting; }

    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }

    public String getSupervision() { return supervision; }
    public void setSupervision(String supervision) { this.supervision = supervision; }

    public String getFollowUp() { return followUp; }
    public void setFollowUp(String followUp) { this.followUp = followUp; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}