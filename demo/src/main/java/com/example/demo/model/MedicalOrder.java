package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_orders")
public class MedicalOrder {

    public enum ReviewStatus {
        PENDING_REVIEW,
        CONFIRMED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String documentType;
    private String patientName;
    private String actionPlan;

    @Column(columnDefinition = "TEXT")
    private String detectedItems;

    // The full cleaned transcript Phi-3 produced, kept alongside the order so
    // a human reviewer can actually see what the document said before
    // confirming any action -- not just the derived summary fields.
    @Column(columnDefinition = "TEXT")
    private String rawTranscript;

    // "trocr" or "paddleocr_fallback" -- lets a reviewer weigh confidence
    // appropriately; paddleocr_fallback output should be treated as lower
    // trust given its known accuracy issues on handwriting.
    private String recognitionEngine;

    // Every order starts unreviewed. Nothing downstream should treat an
    // order as real/actionable until a human explicitly sets this to
    // CONFIRMED via the review endpoint.
    @Enumerated(EnumType.STRING)
    private ReviewStatus status = ReviewStatus.PENDING_REVIEW;

    private String reviewedBy;
    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    public MedicalOrder() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getActionPlan() { return actionPlan; }
    public void setActionPlan(String actionPlan) { this.actionPlan = actionPlan; }

    public String getDetectedItems() { return detectedItems; }
    public void setDetectedItems(String detectedItems) { this.detectedItems = detectedItems; }

    public String getRawTranscript() { return rawTranscript; }
    public void setRawTranscript(String rawTranscript) { this.rawTranscript = rawTranscript; }

    public String getRecognitionEngine() { return recognitionEngine; }
    public void setRecognitionEngine(String recognitionEngine) { this.recognitionEngine = recognitionEngine; }

    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}