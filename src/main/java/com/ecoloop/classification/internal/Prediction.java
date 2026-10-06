package com.ecoloop.classification.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "predictions")
public class Prediction {

    @Id
    private UUID id;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(length = 100)
    private String provider;

    @Column(length = 255)
    private String model;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Prediction() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
