package com.ecoloop.rewards;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reward_ledger")
public class RewardLedger {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RewardLedger() {}

    public RewardLedger(UUID userId, int points, String type) {
        this.userId = userId;
        this.points = points;
        this.type = type;
    }

    public RewardLedger(UUID userId, int points, String type, String description, UUID referenceId) {
        this.userId = userId;
        this.points = points;
        this.type = type;
        this.description = description;
        this.referenceId = referenceId;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
