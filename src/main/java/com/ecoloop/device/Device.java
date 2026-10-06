package com.ecoloop.device;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(length = 50)
    private String category;

    @Column(name = "condition", length = 50)
    private String condition;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "ai_category", length = 50)
    private String aiCategory;

    @Column(name = "ai_confidence", precision = 5, scale = 4)
    private BigDecimal aiConfidence;

    @Column(name = "ai_status", nullable = false, length = 20)
    private String aiStatus = "pending";

    @Column(name = "ai_provider", length = 100)
    private String aiProvider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Device() {}

    public Device(UUID userId, String condition, String category, BigDecimal aiConfidence, String aiStatus) {
        this.userId = userId;
        this.condition = condition;
        this.category = category;
        this.aiCategory = category;
        this.aiConfidence = aiConfidence;
        this.aiStatus = aiStatus;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getAiCategory() { return aiCategory; }
    public void setAiCategory(String aiCategory) { this.aiCategory = aiCategory; }
    public BigDecimal getAiConfidence() { return aiConfidence; }
    public void setAiConfidence(BigDecimal aiConfidence) { this.aiConfidence = aiConfidence; }
    public String getAiStatus() { return aiStatus; }
    public void setAiStatus(String aiStatus) { this.aiStatus = aiStatus; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
