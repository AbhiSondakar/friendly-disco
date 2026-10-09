package com.ecoloop.partner;

import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.security.Role;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.pickup.PickupRequest;
import com.ecoloop.pickup.PickupRepository;
import com.ecoloop.pickup.PickupService;
import com.ecoloop.rewards.RewardLedger;
import com.ecoloop.rewards.RewardLedgerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration gate: verifies the full Partner Delivery Flow end-to-end through the
 * service layer. HTTP-layer tests are covered by PickupServiceIT and PartnerControllerTest.
 *
 * Flow: PENDING → ASSIGNED → IN_TRANSIT → COLLECTED → DELIVERED
 */
@SpringBootTest
@Transactional
class PartnerDeliveryFlowIT {

    @Autowired PickupService pickupService;
    @Autowired PickupRepository pickups;
    @Autowired UserRepository users;
    @Autowired PartnerRepository partners;
    @Autowired RewardLedgerRepository ledger;

    @Test
    void fullDeliveryFlow_statusProgressionAndRewardAwarded() {
        // 1. Setup: household user + pickup
        User household = users.save(new User("flow-household@example.com", "hash", "H", "HOUSEHOLD"));
        User partnerUser = users.save(new User("flow-partner@example.com", "hash", "P", "PARTNER"));

        Partner partner = new Partner(partnerUser.getId(), "FlowOrg", "type", "lic");
        partner.setStatus("approved");
        partner.setWarehouseId("WH-FLOW-001");
        partners.save(partner);

        PickupRequest pickup = pickups.save(
            new PickupRequest(household.getId(), UUID.randomUUID(), "123 Flow St"));

        ActorContext actor = new ActorContext(partnerUser.getId(), Role.PARTNER, partner.getId());

        // 2. Available jobs pool — pickup must appear as unassigned
        List<PickupRequest> available = pickups.findUnassigned();
        assertTrue(available.stream().anyMatch(p -> p.getId().equals(pickup.getId())),
            "Pickup should appear in available jobs pool before claiming");

        // 3. PENDING → ASSIGNED
        PickupRequest assigned = pickupService.claimPickup(actor, pickup.getId());
        assertEquals("accepted", assigned.getStatus());
        assertEquals(partner.getId(), assigned.getPartnerId());
        assertNotNull(assigned.getAssignedAt());

        // 4. Claimed pickup must no longer appear in the available pool
        List<PickupRequest> poolAfterClaim = pickups.findUnassigned();
        assertFalse(poolAfterClaim.stream().anyMatch(p -> p.getId().equals(pickup.getId())),
            "Claimed pickup must not appear in available jobs pool");

        // 5. ASSIGNED → IN_TRANSIT
        PickupRequest inTransit = pickupService.startTransit(actor, pickup.getId());
        assertEquals("accepted", inTransit.getStatus());
        assertNotNull(inTransit.getInTransitAt());

        // 6. IN_TRANSIT → COLLECTED
        PickupRequest collected = pickupService.markCollected(actor, pickup.getId());
        assertEquals("accepted", collected.getStatus());
        assertNotNull(collected.getCollectedAt());

        // 7. COLLECTED → DELIVERED (correct warehouse ID)
        PickupRequest delivered = pickupService.deliverPickup(actor, pickup.getId(), "WH-FLOW-001");
        assertEquals("completed", delivered.getStatus());
        assertNotNull(delivered.getDeliveredAt());
        assertNotNull(delivered.getCompletedAt());

        // 8. Reward ledger entry created for household
        List<RewardLedger> rewards = ledger.findAllByUserId(household.getId());
        assertEquals(1, rewards.size(), "Exactly one reward entry should be created on delivery");
        assertEquals(25, rewards.get(0).getPoints());
        assertEquals("earn", rewards.get(0).getType());

        // 9. Wrong warehouse ID is rejected
        PickupRequest pickup2 = pickups.save(
            new PickupRequest(household.getId(), UUID.randomUUID(), "456 Flow St"));
        pickupService.claimPickup(actor, pickup2.getId());
        pickupService.startTransit(actor, pickup2.getId());
        pickupService.markCollected(actor, pickup2.getId());
        assertThrows(IllegalStateException.class,
            () -> pickupService.deliverPickup(actor, pickup2.getId(), "WH-WRONG"),
            "Wrong warehouse ID must be rejected");
    }

    @Test
    void deliveredJobDoesNotDoubleRewardOnComplete() {
        // Ensure that calling completeJob on an already-delivered pickup does not
        // create a second reward ledger entry (BOQ-3 double-reward guard).
        User household = users.save(new User("no-double@example.com", "hash", "H", "HOUSEHOLD"));
        User partnerUser = users.save(new User("no-double-p@example.com", "hash", "P", "PARTNER"));

        Partner partner = new Partner(partnerUser.getId(), "NoDblOrg", "type", "lic");
        partner.setStatus("approved");
        partner.setWarehouseId("WH-NODBL");
        partners.save(partner);

        PickupRequest pickup = pickups.save(
            new PickupRequest(household.getId(), UUID.randomUUID(), "789 Flow St"));

        ActorContext actor = new ActorContext(partnerUser.getId(), Role.PARTNER, partner.getId());

        pickupService.claimPickup(actor, pickup.getId());
        pickupService.startTransit(actor, pickup.getId());
        pickupService.markCollected(actor, pickup.getId());
        pickupService.deliverPickup(actor, pickup.getId(), "WH-NODBL");

        // Now call completeJob on the delivered pickup (backward-compat path)
        pickupService.completeAssignedPickup(actor, pickup.getId());

        List<RewardLedger> rewards = ledger.findAllByUserId(household.getId());
        assertEquals(1, rewards.size(), "Reward must not be doubled when completing an already-delivered job");
    }
}
