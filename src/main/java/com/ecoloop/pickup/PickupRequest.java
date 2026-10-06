package com.ecoloop.pickup;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pickup_requests")
public class PickupRequest {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "partner_id")
    private UUID partnerId;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "verified_category", length = 50)
    private String verifiedCategory;

    @Column(name = "verified_condition", length = 50)
    private String verifiedCondition;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    @Column(name = "verification_evidence_url", length = 1024)
    private String verificationEvidenceUrl;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PickupRequest() {}

    public PickupRequest(UUID userId, UUID deviceId, String address) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.address = address;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public UUID getPartnerId() { return partnerId; }
    public void setPartnerId(UUID partnerId) { this.partnerId = partnerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getVerifiedCategory() { return verifiedCategory; }
    public void setVerifiedCategory(String verifiedCategory) { this.verifiedCategory = verifiedCategory; }
    public String getVerifiedCondition() { return verifiedCondition; }
    public void setVerifiedCondition(String verifiedCondition) { this.verifiedCondition = verifiedCondition; }
    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String verificationNotes) { this.verificationNotes = verificationNotes; }
    public String getVerificationEvidenceUrl() { return verificationEvidenceUrl; }
    public void setVerificationEvidenceUrl(String verificationEvidenceUrl) { this.verificationEvidenceUrl = verificationEvidenceUrl; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public void verify(String category, String condition, String notes, String evidenceUrl, UUID verifiedBy) {
        this.status = "verified";
        this.verifiedCategory = category;
        this.verifiedCondition = condition;
        this.verificationNotes = notes;
        this.verificationEvidenceUrl = evidenceUrl;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
