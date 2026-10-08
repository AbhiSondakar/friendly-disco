package com.ecoloop.pickup;

import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.security.Role;
import com.ecoloop.device.Device;
import com.ecoloop.device.DeviceRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.notification.NotificationRepository;
import com.ecoloop.rewards.RewardLedgerRepository;
import com.ecoloop.routing.RoutingOffer;
import com.ecoloop.routing.RoutingOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PickupServiceAuthorizationTest {

    @Autowired
    private PickupService pickupService;

    @Autowired
    private PickupRepository pickupRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private RoutingOfferRepository offerRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private RewardLedgerRepository rewardLedgerRepository;

    private UUID householdUserId;
    private UUID otherHouseholdUserId;
    private UUID partnerUserId;
    private Partner approvedPartner;
    private Device householdDevice;
    private PickupRequest pendingPickup;
    private RoutingOffer validOffer;

    @BeforeEach
    void setUp() {
        householdUserId = UUID.randomUUID();
        otherHouseholdUserId = UUID.randomUUID();
        partnerUserId = UUID.randomUUID();

        householdDevice = new Device(householdUserId, "good", "laptop", BigDecimal.valueOf(0.9), "completed");
        householdDevice = deviceRepository.save(householdDevice);

        approvedPartner = new Partner(partnerUserId, "Recycle Corp", "Recycler", "LIC-123");
        approvedPartner.setStatus("approved");
        approvedPartner.setCapacity(5);
        approvedPartner = partnerRepository.save(approvedPartner);

        pendingPickup = new PickupRequest(householdUserId, householdDevice.getId(), "123 Green Way");
        pendingPickup.setStatus("pending");
        pendingPickup.setCreatedAt(Instant.now());
        pendingPickup.setUpdatedAt(Instant.now());
        pendingPickup = pickupRepository.save(pendingPickup);

        validOffer = new RoutingOffer(pendingPickup.getId(), approvedPartner.getId(), Instant.now().plusSeconds(3600));
        validOffer.setStatus("offered");
        validOffer = offerRepository.save(validOffer);
    }

    @Test
    void partnerCannotCreatePickup() {
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);
        CreatePickup request = new CreatePickup(householdDevice.getId(), "123 Green Way", null);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.createPickup(partnerActor, request));
    }

    @Test
    void householdCannotCreatePickupForUnownedDevice() {
        ActorContext otherHousehold = new ActorContext(otherHouseholdUserId, Role.HOUSEHOLD);
        CreatePickup request = new CreatePickup(householdDevice.getId(), "123 Green Way", null);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.createPickup(otherHousehold, request));
    }

    @Test
    void createPickupThrowsNoSuchElementForMissingDevice() {
        ActorContext householdActor = new ActorContext(householdUserId, Role.HOUSEHOLD);
        CreatePickup request = new CreatePickup(UUID.randomUUID(), "123 Green Way", null);

        assertThrows(NoSuchElementException.class, () ->
            pickupService.createPickup(householdActor, request));
    }

    @Test
    void createPickupThrowsIllegalStateWhenActivePickupAlreadyExists() {
        ActorContext householdActor = new ActorContext(householdUserId, Role.HOUSEHOLD);
        CreatePickup request = new CreatePickup(householdDevice.getId(), "123 Green Way", null);

        assertThrows(IllegalStateException.class, () ->
            pickupService.createPickup(householdActor, request));
    }

    @Test
    void partnerCannotCancelPickup() {
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.cancelOwnedPickup(partnerActor, pendingPickup.getId()));
        assertThrows(AccessDeniedException.class, () ->
            pickupService.cancelOwnedPickupByDevice(partnerActor, householdDevice.getId()));
    }

    @Test
    void householdCannotCancelAnotherHouseholdsPickup() {
        ActorContext otherHousehold = new ActorContext(otherHouseholdUserId, Role.HOUSEHOLD);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.cancelOwnedPickup(otherHousehold, pendingPickup.getId()));
    }

    @Test
    void cancelPickupThrowsNoSuchElementWhenNotFound() {
        ActorContext householdActor = new ActorContext(householdUserId, Role.HOUSEHOLD);

        assertThrows(NoSuchElementException.class, () ->
            pickupService.cancelOwnedPickup(householdActor, UUID.randomUUID()));
    }

    @Test
    void cancelPickupThrowsIllegalStateForCompletedPickup() {
        pendingPickup.setStatus("completed");
        pickupRepository.save(pendingPickup);

        ActorContext householdActor = new ActorContext(householdUserId, Role.HOUSEHOLD);

        assertThrows(IllegalStateException.class, () ->
            pickupService.cancelOwnedPickup(householdActor, pendingPickup.getId()));
    }

    @Test
    void householdCannotAcceptPickup() {
        ActorContext householdActor = new ActorContext(householdUserId, Role.HOUSEHOLD);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.acceptOfferedPickup(householdActor, pendingPickup.getId(), validOffer.getId()));
    }

    @Test
    void adminCannotAcceptPickup() {
        ActorContext adminActor = new ActorContext(UUID.randomUUID(), Role.ADMIN);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.acceptOfferedPickup(adminActor, pendingPickup.getId(), validOffer.getId()));
    }

    @Test
    void acceptPickupThrowsNoSuchElementWhenPickupMissing() {
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);
        UUID missingPickupId = UUID.randomUUID();
        RoutingOffer offerForMissing = offerRepository.save(
            new RoutingOffer(missingPickupId, approvedPartner.getId(), Instant.now().plusSeconds(3600))
        );

        assertThrows(NoSuchElementException.class, () ->
            pickupService.acceptOfferedPickup(partnerActor, missingPickupId, offerForMissing.getId()));
    }

    @Test
    void acceptPickupThrowsIllegalStateWhenNotPending() {
        pendingPickup.setStatus("cancelled");
        pickupRepository.save(pendingPickup);

        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);

        assertThrows(IllegalStateException.class, () ->
            pickupService.acceptOfferedPickup(partnerActor, pendingPickup.getId(), validOffer.getId()));
    }

    @Test
    void partnerCannotAcceptPickupWithoutOfferId() {
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.acceptOfferedPickup(partnerActor, pendingPickup.getId(), null));
    }

    @Test
    void partnerCannotAcceptPickupWithNonExistentOffer() {
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.acceptOfferedPickup(partnerActor, pendingPickup.getId(), UUID.randomUUID()));
    }

    @Test
    void partnerCannotAcceptPickupWithAnotherPartnersOffer() {
        Partner otherPartner = partnerRepository.save(new Partner(UUID.randomUUID(), "Other", "Recycler", "LIC-999"));
        RoutingOffer otherOffer = offerRepository.save(new RoutingOffer(pendingPickup.getId(), otherPartner.getId(), Instant.now().plusSeconds(3600)));
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.acceptOfferedPickup(partnerActor, pendingPickup.getId(), otherOffer.getId()));
    }

    @Test
    void authenticatedPartnerUserCannotForgeAnotherPartnerProfileId() {
        Partner otherPartner = new Partner(UUID.randomUUID(), "Other", "Recycler", "LIC-999");
        otherPartner.setStatus("approved");
        otherPartner = partnerRepository.save(otherPartner);

        ActorContext forgedProfileActor = new ActorContext(otherPartner.getUserId(), Role.PARTNER, approvedPartner.getId());

        assertThrows(AccessDeniedException.class, () ->
            pickupService.acceptOfferedPickup(forgedProfileActor, pendingPickup.getId(), validOffer.getId()));
    }

    @Test
    void partnerCannotAcceptPickupWithExpiredOffer() {
        RoutingOffer offer = new RoutingOffer(pendingPickup.getId(), approvedPartner.getId(), Instant.now().minusSeconds(60));
        offer.setStatus("offered");
        RoutingOffer expiredOffer = offerRepository.save(offer);
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);

        assertThrows(IllegalStateException.class, () ->
            pickupService.acceptOfferedPickup(partnerActor, pendingPickup.getId(), expiredOffer.getId()));
    }

    @Test
    void partnerCannotAcceptPickupWhenOfferStatusNotOffered() {
        validOffer.setStatus("rejected");
        offerRepository.save(validOffer);
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);

        assertThrows(IllegalStateException.class, () ->
            pickupService.acceptOfferedPickup(partnerActor, pendingPickup.getId(), validOffer.getId()));
    }

    @Test
    void partnerCannotAcceptPickupWhenOfferPickupIdMismatch() {
        Device otherDevice = deviceRepository.save(new Device(householdUserId, "fair", "phone", BigDecimal.valueOf(0.5), "completed"));
        PickupRequest otherPickup = pickupRepository.save(new PickupRequest(householdUserId, otherDevice.getId(), "456 Oak"));
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);

        assertThrows(IllegalStateException.class, () ->
            pickupService.acceptOfferedPickup(partnerActor, otherPickup.getId(), validOffer.getId()));
    }

    @Test
    void partnerCanAcceptPickupWithValidOffer() {
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);

        PickupRequest accepted = pickupService.acceptOfferedPickup(partnerActor, pendingPickup.getId(), validOffer.getId());
        assertEquals("accepted", accepted.getStatus());
        assertEquals(approvedPartner.getId(), accepted.getPartnerId());

        RoutingOffer updatedOffer = offerRepository.findById(validOffer.getId()).orElseThrow();
        assertEquals("accepted", updatedOffer.getStatus());
    }

    @Test
    void householdCannotRejectPickup() {
        ActorContext householdActor = new ActorContext(householdUserId, Role.HOUSEHOLD);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.rejectAssignedPickup(householdActor, pendingPickup.getId(), "reason"));
    }

    @Test
    void unassignedPartnerCannotRejectPickup() {
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.rejectAssignedPickup(partnerActor, pendingPickup.getId(), "reason"));
    }

    @Test
    void householdCannotVerifyPickup() {
        ActorContext householdActor = new ActorContext(householdUserId, Role.HOUSEHOLD);
        VerifyRequest verifyReq = new VerifyRequest("laptop", "good", "notes", "http://url");

        assertThrows(AccessDeniedException.class, () ->
            pickupService.verifyAssignedPickup(householdActor, pendingPickup.getId(), verifyReq));
    }

    @Test
    void householdCannotCompletePickup() {
        ActorContext householdActor = new ActorContext(householdUserId, Role.HOUSEHOLD);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.completeAssignedPickup(householdActor, pendingPickup.getId()));
    }

    @Test
    void unassignedPartnerCannotCompletePickup() {
        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);

        assertThrows(AccessDeniedException.class, () ->
            pickupService.completeAssignedPickup(partnerActor, pendingPickup.getId()));
    }

    // --- reassignPickup ---

    @Test
    void nonAdminCannotReassignPickup() {
        ActorContext householdActor = new ActorContext(householdUserId, Role.HOUSEHOLD);
        assertThrows(AccessDeniedException.class, () ->
            pickupService.reassignPickup(householdActor, pendingPickup.getId(), approvedPartner.getId()));

        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);
        assertThrows(AccessDeniedException.class, () ->
            pickupService.reassignPickup(partnerActor, pendingPickup.getId(), approvedPartner.getId()));
    }

    @Test
    void adminReassignPickupFailsWhenTargetPartnerNotFound() {
        ActorContext adminActor = new ActorContext(UUID.randomUUID(), Role.ADMIN);
        UUID randomPartnerId = UUID.randomUUID();

        assertThrows(NoSuchElementException.class, () ->
            pickupService.reassignPickup(adminActor, pendingPickup.getId(), randomPartnerId));
    }

    @Test
    void adminReassignPickupFailsWhenTargetPartnerNotApproved() {
        ActorContext adminActor = new ActorContext(UUID.randomUUID(), Role.ADMIN);
        Partner pendingPartner = new Partner(UUID.randomUUID(), "Pending Co", "Recycler", "LIC-PND");
        pendingPartner.setStatus("pending");
        Partner savedPendingPartner = partnerRepository.save(pendingPartner);

        assertThrows(IllegalStateException.class, () ->
            pickupService.reassignPickup(adminActor, pendingPickup.getId(), savedPendingPartner.getId()));
    }

    @Test
    void adminReassignPickupFailsWhenTargetPartnerAtCapacity() {
        ActorContext adminActor = new ActorContext(UUID.randomUUID(), Role.ADMIN);
        Partner fullPartner = new Partner(UUID.randomUUID(), "Full Co", "Recycler", "LIC-FULL");
        fullPartner.setStatus("approved");
        fullPartner.setCapacity(1);
        Partner savedFullPartner = partnerRepository.save(fullPartner);

        PickupRequest existingJob = new PickupRequest(householdUserId, householdDevice.getId(), "Some address");
        existingJob.setStatus("accepted");
        existingJob.setPartnerId(savedFullPartner.getId());
        pickupRepository.save(existingJob);

        assertThrows(IllegalStateException.class, () ->
            pickupService.reassignPickup(adminActor, pendingPickup.getId(), savedFullPartner.getId()));
    }

    @Test
    void adminReassignPickupFailsWhenPickupCompletedOrCancelled() {
        ActorContext adminActor = new ActorContext(UUID.randomUUID(), Role.ADMIN);

        pendingPickup.setStatus("completed");
        pickupRepository.save(pendingPickup);
        assertThrows(IllegalStateException.class, () ->
            pickupService.reassignPickup(adminActor, pendingPickup.getId(), approvedPartner.getId()));

        pendingPickup.setStatus("cancelled");
        pickupRepository.save(pendingPickup);
        assertThrows(IllegalStateException.class, () ->
            pickupService.reassignPickup(adminActor, pendingPickup.getId(), approvedPartner.getId()));
    }

    @Test
    void adminReassignPickupSucceedsAndNotifiesParties() {
        ActorContext adminActor = new ActorContext(UUID.randomUUID(), Role.ADMIN);

        Partner partner2 = new Partner(UUID.randomUUID(), "Partner 2", "Recycler", "LIC-2");
        partner2.setStatus("approved");
        partner2.setCapacity(5);
        partner2 = partnerRepository.save(partner2);

        pendingPickup.setStatus("accepted");
        pendingPickup.setPartnerId(approvedPartner.getId());
        pickupRepository.save(pendingPickup);

        PickupRequest reassigned = pickupService.reassignPickup(adminActor, pendingPickup.getId(), partner2.getId());

        assertEquals("accepted", reassigned.getStatus());
        assertEquals(partner2.getId(), reassigned.getPartnerId());

        var householdNotifs = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(householdUserId);
        assertFalse(householdNotifs.isEmpty());
        assertTrue(householdNotifs.stream().anyMatch(n -> "pickup_reassigned".equals(n.getType())));

        var prevPartnerNotifs = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(approvedPartner.getUserId());
        assertFalse(prevPartnerNotifs.isEmpty());
        assertTrue(prevPartnerNotifs.stream().anyMatch(n -> "pickup_reassigned".equals(n.getType())));
    }

    @Test
    void reassignmentRetryIsIdempotentButALaterReassignmentNotifiesAgain() {
        ActorContext adminActor = new ActorContext(UUID.randomUUID(), Role.ADMIN);
        Partner partner2 = new Partner(UUID.randomUUID(), "Partner 2", "Recycler", "LIC-2");
        partner2.setStatus("approved");
        partner2.setCapacity(5);
        partner2 = partnerRepository.save(partner2);

        pendingPickup.setStatus("accepted");
        pendingPickup.setPartnerId(approvedPartner.getId());
        pickupRepository.save(pendingPickup);

        pickupService.reassignPickup(adminActor, pendingPickup.getId(), partner2.getId());
        assertEquals(1, reassignmentNotificationsFor(householdUserId));

        // Same target after a successful commit is the retried action, not a new reassignment.
        pickupService.reassignPickup(adminActor, pendingPickup.getId(), partner2.getId());
        assertEquals(1, reassignmentNotificationsFor(householdUserId));

        pickupService.reassignPickup(adminActor, pendingPickup.getId(), approvedPartner.getId());
        pickupService.reassignPickup(adminActor, pendingPickup.getId(), partner2.getId());
        assertEquals(3, reassignmentNotificationsFor(householdUserId));
    }

    @Test
    void cancellingAcceptedPickupNotifiesAssignedAndPreviouslyOfferedPartners() {
        Partner otherPartner = new Partner(UUID.randomUUID(), "Other", "Recycler", "LIC-999");
        otherPartner.setStatus("approved");
        otherPartner = partnerRepository.save(otherPartner);

        pendingPickup.setStatus("accepted");
        pendingPickup.setPartnerId(approvedPartner.getId());
        pickupRepository.save(pendingPickup);

        validOffer.setStatus("accepted");
        offerRepository.save(validOffer);
        RoutingOffer otherOffer = offerRepository.save(new RoutingOffer(
            pendingPickup.getId(), otherPartner.getId(), Instant.now().plusSeconds(3600)));

        pickupService.cancelOwnedPickup(new ActorContext(householdUserId, Role.HOUSEHOLD), pendingPickup.getId());

        assertEquals("cancelled", offerRepository.findById(validOffer.getId()).orElseThrow().getStatus());
        assertEquals("cancelled", offerRepository.findById(otherOffer.getId()).orElseThrow().getStatus());
        assertEquals(1, cancelledNotificationsFor(approvedPartner.getUserId()));
        assertEquals(1, cancelledNotificationsFor(otherPartner.getUserId()));
    }

    @Test
    void completingPickupTwiceCreatesOneRewardAndOneHouseholdNotification() {
        pendingPickup.setStatus("accepted");
        pendingPickup.setPartnerId(approvedPartner.getId());
        pickupRepository.save(pendingPickup);

        ActorContext partnerActor = new ActorContext(partnerUserId, Role.PARTNER);
        pickupService.completeAssignedPickup(partnerActor, pendingPickup.getId());
        pickupService.completeAssignedPickup(partnerActor, pendingPickup.getId());

        assertTrue(rewardLedgerRepository.findByUserIdAndReferenceId(householdUserId, pendingPickup.getId()).isPresent());
        assertEquals(1, rewardLedgerRepository.findAllByUserId(householdUserId).stream()
            .filter(reward -> pendingPickup.getId().equals(reward.getReferenceId()))
            .count());
        assertEquals(1, notificationRepository.findAllByUserIdOrderByCreatedAtDesc(householdUserId).stream()
            .filter(notification -> "pickup_completed".equals(notification.getType()))
            .count());
    }

    private long reassignmentNotificationsFor(UUID userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
            .filter(notification -> "pickup_reassigned".equals(notification.getType()))
            .count();
    }

    private long cancelledNotificationsFor(UUID userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
            .filter(notification -> "pickup_cancelled".equals(notification.getType()))
            .count();
    }
}
