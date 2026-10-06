package com.ecoloop.rewards;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "redemptions")
public class Redemption {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "points_cost", nullable = false)
    private int pointsCost;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Redemption() {}

    public Redemption(UUID userId, UUID catalogItemId, int pointsCost) {
        this.userId = userId;
        this.catalogItemId = catalogItemId;
        this.pointsCost = pointsCost;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public void setCatalogItemId(UUID catalogItemId) { this.catalogItemId = catalogItemId; }
    public int getPointsCost() { return pointsCost; }
    public void setPointsCost(int pointsCost) { this.pointsCost = pointsCost; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
