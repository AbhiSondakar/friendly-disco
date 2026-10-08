package com.ecoloop.pickup;

import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.security.Role;
import com.ecoloop.routing.RoutingOffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PickupRequestAggregateTest {

    private UUID householdId;
    private UUID otherHouseholdId;
    private UUID partnerId;
    private UUID otherPartnerId;
    private UUID deviceId;
    private PickupRequest pickup;

    @BeforeEach
    void setUp() {
        householdId = UUID.randomUUID();
        otherHouseholdId = UUID.randomUUID();
        partnerId = UUID.randomUUID();
        otherPartnerId = UUID.randomUUID();
        deviceId = UUID.randomUUID();

        pickup = new PickupRequest(householdId, deviceId, "123 Green St");
        pickup.setStatus("pending");
    }

    // --- acceptBy ---

    @Test
    void acceptBySucceedsForPartnerWithMatchingOffer() {
        RoutingOffer offer = new RoutingOffer(pickup.getId(), partnerId, Instant.now().plusSeconds(3600));
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        pickup.acceptBy(actor, offer);

        assertEquals("accepted", pickup.getStatus());
        assertEquals(partnerId, pickup.getPartnerId());
        assertFalse(pickup.getDomainEvents().isEmpty());
        assertTrue(pickup.getDomainEvents().stream().anyMatch(e -> e instanceof PickupAcceptedEvent));
    }

    @Test
    void acceptByFailsForHousehold() {
        RoutingOffer offer = new RoutingOffer(pickup.getId(), partnerId, Instant.now().plusSeconds(3600));
        ActorContext actor = new ActorContext(householdId, Role.HOUSEHOLD);

        assertThrows(AccessDeniedException.class, () -> pickup.acceptBy(actor, offer));
    }

    @Test
    void acceptByFailsForNullOffer() {
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        assertThrows(AccessDeniedException.class, () -> pickup.acceptBy(actor, null));
    }

    @Test
    void acceptByFailsWhenOfferPickupMismatch() {
        RoutingOffer offer = new RoutingOffer(UUID.randomUUID(), partnerId, Instant.now().plusSeconds(3600));
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        assertThrows(IllegalStateException.class, () -> pickup.acceptBy(actor, offer));
    }

    @Test
    void acceptByFailsWhenOfferPartnerMismatch() {
        RoutingOffer offer = new RoutingOffer(pickup.getId(), otherPartnerId, Instant.now().plusSeconds(3600));
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        assertThrows(AccessDeniedException.class, () -> pickup.acceptBy(actor, offer));
    }

    @Test
    void acceptByFailsWhenStatusNotPending() {
        pickup.setStatus("accepted");
        RoutingOffer offer = new RoutingOffer(pickup.getId(), partnerId, Instant.now().plusSeconds(3600));
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        assertThrows(IllegalStateException.class, () -> pickup.acceptBy(actor, offer));
    }

    // --- cancelBy ---

    @Test
    void cancelBySucceedsForOwningHousehold() {
        ActorContext actor = new ActorContext(householdId, Role.HOUSEHOLD);

        pickup.cancelBy(actor);

        assertEquals("cancelled", pickup.getStatus());
        assertTrue(pickup.getDomainEvents().stream().anyMatch(e -> e instanceof PickupCancelledEvent));
    }

    @Test
    void cancelBySucceedsForAdmin() {
        ActorContext admin = new ActorContext(UUID.randomUUID(), Role.ADMIN);

        pickup.cancelBy(admin);

        assertEquals("cancelled", pickup.getStatus());
        assertTrue(pickup.getDomainEvents().stream().anyMatch(e -> e instanceof PickupCancelledEvent));
    }

    @Test
    void cancelBySucceedsWhenAccepted() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext actor = new ActorContext(householdId, Role.HOUSEHOLD);

        pickup.cancelBy(actor);

        assertEquals("cancelled", pickup.getStatus());
        assertTrue(pickup.getDomainEvents().stream().anyMatch(e -> e instanceof PickupCancelledEvent));
    }

    @Test
    void cancelByFailsForNonOwningHousehold() {
        ActorContext otherActor = new ActorContext(otherHouseholdId, Role.HOUSEHOLD);

        assertThrows(AccessDeniedException.class, () -> pickup.cancelBy(otherActor));
    }

    @Test
    void cancelByFailsForPartner() {
        ActorContext partnerActor = new ActorContext(partnerId, Role.PARTNER);

        assertThrows(AccessDeniedException.class, () -> pickup.cancelBy(partnerActor));
    }

    @Test
    void cancelByFailsForVerifiedStatus() {
        pickup.setStatus("verified");
        pickup.setPartnerId(partnerId);
        ActorContext actor = new ActorContext(householdId, Role.HOUSEHOLD);

        assertThrows(IllegalStateException.class, () -> pickup.cancelBy(actor));
    }

    @Test
    void cancelByFailsForCompletedStatus() {
        pickup.setStatus("completed");
        ActorContext actor = new ActorContext(householdId, Role.HOUSEHOLD);

        assertThrows(IllegalStateException.class, () -> pickup.cancelBy(actor));
    }

    // --- rejectBy ---

    @Test
    void rejectBySucceedsForAssignedPartner() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        pickup.rejectBy(actor, "Too heavy");

        assertEquals("pending", pickup.getStatus());
        assertNull(pickup.getPartnerId());
        assertTrue(pickup.getDomainEvents().stream().anyMatch(e -> e instanceof PickupCreatedEvent));
    }

    @Test
    void rejectByFailsForUnassignedPartner() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext otherPartnerActor = new ActorContext(UUID.randomUUID(), Role.PARTNER, otherPartnerId);

        assertThrows(AccessDeniedException.class, () -> pickup.rejectBy(otherPartnerActor, "No time"));
    }

    @Test
    void rejectByFailsForHousehold() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext householdActor = new ActorContext(householdId, Role.HOUSEHOLD);

        assertThrows(AccessDeniedException.class, () -> pickup.rejectBy(householdActor, "reason"));
    }

    @Test
    void rejectByFailsWhenStatusIsNotAccepted() {
        pickup.setStatus("pending");
        pickup.setPartnerId(partnerId);
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        assertThrows(IllegalStateException.class, () -> pickup.rejectBy(actor, "reason"));
    }

    // --- verifyBy ---

    @Test
    void verifyBySucceedsForAssignedPartner() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);
        VerifyRequest verifyReq = new VerifyRequest("laptop", "good", "all good", "http://photo");

        pickup.verifyBy(actor, verifyReq);

        assertEquals("verified", pickup.getStatus());
        assertEquals("laptop", pickup.getVerifiedCategory());
        assertEquals("good", pickup.getVerifiedCondition());
        assertEquals("all good", pickup.getVerificationNotes());
        assertEquals("http://photo", pickup.getVerificationEvidenceUrl());
        assertEquals(partnerId, pickup.getVerifiedBy());
        assertNotNull(pickup.getVerifiedAt());
        assertTrue(pickup.getDomainEvents().stream().anyMatch(e -> e instanceof PickupVerifiedEvent));
    }

    @Test
    void verifyByFailsForUnassignedPartner() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext otherPartnerActor = new ActorContext(UUID.randomUUID(), Role.PARTNER, otherPartnerId);
        VerifyRequest verifyReq = new VerifyRequest("laptop", "good", "notes", "http://photo");

        assertThrows(AccessDeniedException.class, () -> pickup.verifyBy(otherPartnerActor, verifyReq));
    }

    @Test
    void verifyByFailsForHousehold() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext householdActor = new ActorContext(householdId, Role.HOUSEHOLD);
        VerifyRequest verifyReq = new VerifyRequest("laptop", "good", "notes", "http://photo");

        assertThrows(AccessDeniedException.class, () -> pickup.verifyBy(householdActor, verifyReq));
    }

    // --- completeBy ---

    @Test
    void completeBySucceedsForAssignedPartnerFromAccepted() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        pickup.completeBy(actor);

        assertEquals("completed", pickup.getStatus());
        assertNotNull(pickup.getCompletedAt());
        assertTrue(pickup.getDomainEvents().stream().anyMatch(e -> e instanceof PickupCompletedEvent));
    }

    @Test
    void completeBySucceedsForAssignedPartnerFromVerified() {
        pickup.setStatus("verified");
        pickup.setPartnerId(partnerId);
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        pickup.completeBy(actor);

        assertEquals("completed", pickup.getStatus());
        assertNotNull(pickup.getCompletedAt());
    }

    @Test
    void completeByIsIdempotentWhenAlreadyCompleted() {
        pickup.setStatus("completed");
        pickup.setPartnerId(partnerId);
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        pickup.completeBy(actor);

        assertEquals("completed", pickup.getStatus());
    }

    @Test
    void completeByFailsForHousehold() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext householdActor = new ActorContext(householdId, Role.HOUSEHOLD);

        assertThrows(AccessDeniedException.class, () -> pickup.completeBy(householdActor));
    }

    @Test
    void completeByFailsForUnassignedPartner() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext otherActor = new ActorContext(UUID.randomUUID(), Role.PARTNER, otherPartnerId);

        assertThrows(AccessDeniedException.class, () -> pickup.completeBy(otherActor));
    }

    @Test
    void completeByFailsWhenStatusPending() {
        pickup.setStatus("pending");
        pickup.setPartnerId(partnerId);
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        assertThrows(IllegalStateException.class, () -> pickup.completeBy(actor));
    }

    @Test
    void legacyInProgressStateIsRejectedByEveryPartnerTransition() {
        pickup.setStatus("in_progress");
        pickup.setPartnerId(partnerId);
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);

        assertThrows(IllegalStateException.class, () -> pickup.rejectBy(actor, "reason"));
        assertThrows(IllegalStateException.class, () -> pickup.verifyBy(actor,
            new VerifyRequest("laptop", "good", "notes", "https://example.test/evidence")));
        assertThrows(IllegalStateException.class, () -> pickup.completeBy(actor));
    }

    // --- reassignBy ---

    @Test
    void reassignBySucceedsForAdmin() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext admin = new ActorContext(UUID.randomUUID(), Role.ADMIN);

        pickup.reassignBy(admin, otherPartnerId);

        assertEquals(otherPartnerId, pickup.getPartnerId());
        assertTrue(pickup.getDomainEvents().stream().anyMatch(e -> e instanceof PickupReassignedEvent));
    }

    @Test
    void reassignByFailsForNonAdmin() {
        pickup.setStatus("accepted");
        pickup.setPartnerId(partnerId);
        ActorContext partnerActor = new ActorContext(partnerId, Role.PARTNER);

        assertThrows(AccessDeniedException.class, () -> pickup.reassignBy(partnerActor, otherPartnerId));
    }

    @Test
    void reassignByFailsWhenCompletedOrCancelled() {
        pickup.setStatus("completed");
        pickup.setPartnerId(partnerId);
        ActorContext admin = new ActorContext(UUID.randomUUID(), Role.ADMIN);

        assertThrows(IllegalStateException.class, () -> pickup.reassignBy(admin, otherPartnerId));

        pickup.setStatus("cancelled");
        assertThrows(IllegalStateException.class, () -> pickup.reassignBy(admin, otherPartnerId));
    }
}
