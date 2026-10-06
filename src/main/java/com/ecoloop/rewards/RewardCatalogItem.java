package com.ecoloop.rewards;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reward_catalog")
public class RewardCatalogItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "points_cost", nullable = false)
    private int pointsCost;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RewardCatalogItem() {}

    public RewardCatalogItem(String name, String description, int pointsCost) {
        this.name = name;
        this.description = description;
        this.pointsCost = pointsCost;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPointsCost() { return pointsCost; }
    public void setPointsCost(int pointsCost) { this.pointsCost = pointsCost; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
