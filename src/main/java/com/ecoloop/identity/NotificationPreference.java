package com.ecoloop.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "pickup_updates", nullable = false)
    private boolean pickupUpdates = true;

    @Column(name = "points_updates", nullable = false)
    private boolean pointsUpdates = true;

    @Column(name = "offer_alerts", nullable = false)
    private boolean offerAlerts = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected NotificationPreference() {}

    public NotificationPreference(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public boolean getPickupUpdates() { return pickupUpdates; }
    public void setPickupUpdates(boolean pickupUpdates) { this.pickupUpdates = pickupUpdates; }
    public boolean getPointsUpdates() { return pointsUpdates; }
    public void setPointsUpdates(boolean pointsUpdates) { this.pointsUpdates = pointsUpdates; }
    public boolean getOfferAlerts() { return offerAlerts; }
    public void setOfferAlerts(boolean offerAlerts) { this.offerAlerts = offerAlerts; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
