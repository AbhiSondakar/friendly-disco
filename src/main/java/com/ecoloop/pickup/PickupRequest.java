package com.ecoloop.pickup;

import com.ecoloop.common.security.ActorContext;
import com.ecoloop.routing.RoutingOffer;
import jakarta.persistence.*;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "pickup_requests")
public class PickupRequest extends AbstractAggregateRoot<PickupRequest> {

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

    public PickupRequest registerCreatedEvent() {
        registerEvent(new PickupCreatedEvent(this.id));
        return this;
    }

    public java.util.Collection<Object> getDomainEvents() {
        return domainEvents();
    }

    public PickupRequest acceptBy(ActorContext actor, RoutingOffer offer) {
        if (actor == null || !actor.isPartner()) {
            throw new AccessDeniedException("Only approved partners can accept pickups");
        }
        if (offer == null) {
            throw new AccessDeniedException("Offer is required to accept pickup");
        }
        if (!this.id.equals(offer.getPickupId())) {
            throw new IllegalStateException("Offer is not for this pickup");
        }
        if (actor.partnerId() != null && !actor.partnerId().equals(offer.getPartnerId())) {
            throw new AccessDeniedException("Offer does not belong to this partner");
        }
        if (!"pending".equals(this.status)) {
            throw new IllegalStateException("Pickup is no longer pending (current: " + this.status + ")");
        }

        this.partnerId = offer.getPartnerId();
        this.status = "accepted";
        this.updatedAt = Instant.now();
        registerEvent(new PickupAcceptedEvent(this.id, this.partnerId));
        return this;
    }

    public PickupRequest cancelBy(ActorContext actor) {
        if (actor == null || (!actor.isHousehold() && !actor.isAdmin())) {
            throw new AccessDeniedException("Only households can cancel their pickups");
        }
        if (!actor.isAdmin() && !actor.userId().equals(this.userId)) {
            throw new AccessDeniedException("Pickup does not belong to this user");
        }
        if (!Set.of("pending", "accepted").contains(this.status)) {
            throw new IllegalStateException("Invalid pickup state transition");
        }

        this.status = "cancelled";
        this.updatedAt = Instant.now();
        registerEvent(new PickupCancelledEvent(this.id));
        return this;
    }

    public PickupRequest rejectBy(ActorContext actor, String reason) {
        if (actor == null || !actor.isPartner()) {
            throw new AccessDeniedException("Only assigned partner can reject pickup");
        }
        if (actor.partnerId() == null || !actor.partnerId().equals(this.partnerId)) {
            throw new AccessDeniedException("Pickup is not assigned to this partner");
        }
        if (!"accepted".equals(this.status)) {
            throw new IllegalStateException("Invalid pickup state transition");
        }

        UUID prevPartner = this.partnerId;
        this.partnerId = null;
        this.status = "pending";
        this.updatedAt = Instant.now();
        registerEvent(new PickupRejectedEvent(this.id, prevPartner));
        registerEvent(new PickupCreatedEvent(this.id));
        return this;
    }

    public PickupRequest verifyBy(ActorContext actor, VerifyRequest request) {
        if (actor == null || !actor.isPartner()) {
            throw new AccessDeniedException("Only assigned partner can verify pickup");
        }
        if (actor.partnerId() == null || !actor.partnerId().equals(this.partnerId)) {
            throw new AccessDeniedException("Pickup is not assigned to this partner");
        }
        if (!"accepted".equals(this.status)) {
            throw new IllegalStateException("Pickup must be accepted before verification");
        }

        this.verify(
            request != null ? request.category() : null,
            request != null ? request.condition() : null,
            request != null ? request.notes() : null,
            request != null ? request.evidenceUrl() : null,
            actor.userId()
        );
        registerEvent(new PickupVerifiedEvent(this.id, this.partnerId));
        return this;
    }

    public PickupRequest completeBy(ActorContext actor) {
        if (actor == null || !actor.isPartner()) {
            throw new AccessDeniedException("Only assigned partner can complete pickup");
        }
        if (actor.partnerId() == null || !actor.partnerId().equals(this.partnerId)) {
            throw new AccessDeniedException("Pickup is not assigned to this partner");
        }
        if ("completed".equals(this.status)) {
            return this;
        }
        if (!Set.of("accepted", "verified").contains(this.status)) {
            throw new IllegalStateException("Pickup cannot be completed from status: " + this.status);
        }

        this.status = "completed";
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
        registerEvent(new PickupCompletedEvent(this.id, this.partnerId));
        return this;
    }

    public PickupRequest reassignBy(ActorContext actor, UUID newPartnerId) {
        if (actor == null || !actor.isAdmin()) {
            throw new AccessDeniedException("Only admins can reassign pickups");
        }
        if (newPartnerId == null) {
            throw new IllegalArgumentException("newPartnerId is required");
        }
        if (Set.of("completed", "cancelled").contains(this.status)) {
            throw new IllegalStateException("Cannot reassign completed or cancelled pickup");
        }

        UUID previousPartnerId = this.partnerId;
        if (newPartnerId.equals(previousPartnerId)) {
            return this;
        }
        this.partnerId = newPartnerId;
        this.status = "accepted";
        this.updatedAt = Instant.now();
        registerEvent(new PickupReassignedEvent(this.id, previousPartnerId, newPartnerId, UUID.randomUUID()));
        return this;
    }

    /** Cancels an active assignment when the assigned partner is suspended. */
    public boolean cancelForPartnerSuspension(UUID suspendedPartnerId) {
        if (!suspendedPartnerId.equals(this.partnerId)) {
            throw new IllegalArgumentException("Pickup is not assigned to the suspended partner");
        }
        if (!Set.of("accepted", "verified").contains(this.status)) {
            return false;
        }

        this.status = "cancelled";
        this.updatedAt = Instant.now();
        registerEvent(new PickupCancelledEvent(this.id));
        return true;
    }

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
