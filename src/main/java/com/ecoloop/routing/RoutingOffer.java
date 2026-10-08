package com.ecoloop.routing;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routing_offers")
public class RoutingOffer {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "pickup_id", nullable = false)
    private UUID pickupId;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(nullable = false, length = 20)
    private String status = "offered";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "score")
    private Double score;

    @Column(name = "round", nullable = false)
    private int round = 1;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RoutingOffer() {}

    public RoutingOffer(UUID pickupId, UUID partnerId, Instant expiresAt) {
        this(pickupId, partnerId, expiresAt, 1);
    }

    public RoutingOffer(UUID pickupId, UUID partnerId, Instant expiresAt, int round) {
        this.pickupId = pickupId;
        this.partnerId = partnerId;
        this.expiresAt = expiresAt;
        this.round = round;
    }

    public RoutingOffer(UUID pickupId, UUID partnerId, Instant expiresAt, Double score) {
        this(pickupId, partnerId, expiresAt, score, 1);
    }

    public RoutingOffer(UUID pickupId, UUID partnerId, Instant expiresAt, Double score, int round) {
        this.pickupId = pickupId;
        this.partnerId = partnerId;
        this.expiresAt = expiresAt;
        this.score = score;
        this.round = round;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPickupId() { return pickupId; }
    public void setPickupId(UUID pickupId) { this.pickupId = pickupId; }
    public UUID getPartnerId() { return partnerId; }
    public void setPartnerId(UUID partnerId) { this.partnerId = partnerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public void accept() {
        if (!"offered".equals(status) || (expiresAt != null && expiresAt.isBefore(Instant.now()))) {
            throw new IllegalStateException("Offer is no longer available");
        }
        status = "accepted";
    }

    public void reject() {
        reject(null);
    }

    public void reject(String reason) {
        if (!"offered".equals(status) || (expiresAt != null && expiresAt.isBefore(Instant.now()))) {
            throw new IllegalStateException("Offer is no longer available");
        }
        status = "rejected";
        this.rejectionReason = reason;
    }
}
