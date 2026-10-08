package com.ecoloop.routing;

import com.ecoloop.audit.AuditLog;
import com.ecoloop.audit.AuditLogRepository;
import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.security.Role;
import com.ecoloop.device.Device;
import com.ecoloop.device.DeviceRepository;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.notification.Notification;
import com.ecoloop.notification.NotificationRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.pickup.CreatePickup;
import com.ecoloop.pickup.PickupRequest;
import com.ecoloop.pickup.PickupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "ecoloop.routing.offer-expiration.enabled=true",
    "ecoloop.routing.offer-expiration.interval-ms=999999999"
})
@ActiveProfiles("test")
class RoutingCompletenessIntegrationTest {

    @Autowired
    private PickupService pickupService;

    @Autowired
    private RoutingService routingService;

    @Autowired
    private RoutingOfferRepository offerRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RoutingOfferExpirationScheduler expirationScheduler;

    private UUID householdUserId;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        offerRepository.deleteAll();
        partnerRepository.deleteAll();
        deviceRepository.deleteAll();
        notificationRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        householdUserId = UUID.randomUUID();
        User householdUser = new User("household@ecoloop.test", "hash", "Household User", "HOUSEHOLD");
        householdUser.setId(householdUserId);
        userRepository.save(householdUser);

        adminUserId = UUID.randomUUID();
        User adminUser = new User("admin@ecoloop.test", "hash", "Admin User", "ADMIN");
        adminUser.setId(adminUserId);
        userRepository.save(adminUser);
    }

    private Partner createApprovedPartner(String orgName, String serviceAreas, String capabilities, int capacity) {
        UUID partnerUserId = UUID.randomUUID();
        User user = new User(orgName.toLowerCase().replace(" ", "") + "@partner.test", "hash", orgName, "PARTNER");
        user.setId(partnerUserId);
        userRepository.save(user);

        Partner partner = new Partner(partnerUserId, orgName, "Recycler", "LIC-" + UUID.randomUUID());
        partner.setStatus("approved");
        partner.setServiceAreas(serviceAreas);
        partner.setCapabilities(capabilities);
        partner.setCapacity(capacity);
        partner.setRating(BigDecimal.valueOf(4.5));
        return partnerRepository.save(partner);
    }

    @Test
    void serviceAreaFilteringExcludesPartnersOutsideServiceArea() {
        Partner seattlePartner = createApprovedPartner("Seattle Recycler", "Seattle, Bellevue", "laptop", 10);
        Partner portlandPartner = createApprovedPartner("Portland Recycler", "Portland, Eugene", "laptop", 10);
        Partner globalPartner = createApprovedPartner("Global Recycler", null, "laptop", 10);

        Device device = deviceRepository.save(new Device(householdUserId, "good", "laptop", BigDecimal.valueOf(0.95), "completed"));
        ActorContext actor = new ActorContext(householdUserId, Role.HOUSEHOLD);

        PickupRequest pickup = pickupService.createPickup(actor, new CreatePickup(device.getId(), "100 Pike St, Seattle, WA", null));

        List<RoutingOffer> offers = offerRepository.findAllByPickupId(pickup.getId());
        assertEquals(2, offers.size(), "Should only dispatch offers to matching and unrestricted partners");

        List<UUID> offeredPartnerIds = offers.stream().map(RoutingOffer::getPartnerId).toList();
        assertTrue(offeredPartnerIds.contains(seattlePartner.getId()));
        assertTrue(offeredPartnerIds.contains(globalPartner.getId()));
        assertFalse(offeredPartnerIds.contains(portlandPartner.getId()), "Portland partner must be filtered out by service area");
    }

    @Test
    void expiredOffersTriggerNextRoundExcludingPreviousPartners() {
        // Create 2 partners for round 1, and 1 partner for round 2
        Partner partner1 = createApprovedPartner("Partner 1", "Seattle", "laptop", 10);
        Partner partner2 = createApprovedPartner("Partner 2", "Seattle", "laptop", 10);
        Partner partner3 = createApprovedPartner("Partner 3", "Seattle", "laptop", 10);

        // Limit top N: set capacities so partner1 & partner2 have higher rating or score, or ensure 2 offers in round 1
        partner1.setRating(BigDecimal.valueOf(5.0));
        partnerRepository.save(partner1);
        partner2.setRating(BigDecimal.valueOf(4.8));
        partnerRepository.save(partner2);
        partner3.setRating(BigDecimal.valueOf(4.0));
        partnerRepository.save(partner3);

        Device device = deviceRepository.save(new Device(householdUserId, "good", "laptop", BigDecimal.valueOf(0.95), "completed"));
        ActorContext actor = new ActorContext(householdUserId, Role.HOUSEHOLD);

        // Initial pickup creation creates offers
        PickupRequest pickup = pickupService.createPickup(actor, new CreatePickup(device.getId(), "Seattle, WA", null));

        List<RoutingOffer> round1Offers = offerRepository.findAllByPickupId(pickup.getId());
        assertEquals(3, round1Offers.size());
        round1Offers.forEach(o -> assertEquals(1, o.getRound()));

        // Now add a 4th partner who was NOT offered in round 1
        Partner partner4 = createApprovedPartner("Partner 4", "Seattle", "laptop", 10);

        // Expire all round 1 offers
        for (RoutingOffer offer : round1Offers) {
            offer.setExpiresAt(Instant.now().minusSeconds(3600));
            offerRepository.save(offer);
        }

        // Run expiration scheduler cleanup
        expirationScheduler.runExpirationCleanup();

        // Check round 1 offers transitioned to expired
        for (RoutingOffer offer : round1Offers) {
            RoutingOffer reloaded = offerRepository.findById(offer.getId()).orElseThrow();
            assertEquals("expired", reloaded.getStatus());
        }

        // Verify round 2 offers were generated and only include partner4 (since partner1, 2, 3 were already offered)
        List<RoutingOffer> allOffers = offerRepository.findAllByPickupId(pickup.getId());
        List<RoutingOffer> round2Offers = allOffers.stream().filter(o -> o.getRound() == 2).toList();
        assertEquals(1, round2Offers.size(), "Round 2 should only dispatch to partner4");
        assertEquals(partner4.getId(), round2Offers.getFirst().getPartnerId());
    }

    @Test
    void rejectionExcludesPartnerFromSubsequentRounds() {
        Partner partner1 = createApprovedPartner("Partner 1", "Seattle", "laptop", 10);
        Partner partner2 = createApprovedPartner("Partner 2", "Seattle", "laptop", 10);

        Device device = deviceRepository.save(new Device(householdUserId, "good", "laptop", BigDecimal.valueOf(0.95), "completed"));
        ActorContext householdActor = new ActorContext(householdUserId, Role.HOUSEHOLD);

        PickupRequest pickup = pickupService.createPickup(householdActor, new CreatePickup(device.getId(), "Seattle, WA", null));

        List<RoutingOffer> round1Offers = offerRepository.findAllByPickupId(pickup.getId());
        RoutingOffer offer1 = round1Offers.stream().filter(o -> o.getPartnerId().equals(partner1.getId())).findFirst().orElseThrow();
        RoutingOffer offer2 = round1Offers.stream().filter(o -> o.getPartnerId().equals(partner2.getId())).findFirst().orElseThrow();

        // Partner 1 rejects their offer
        routingService.rejectOffer(partner1.getUserId(), offer1.getId(), "No capacity today");
        assertEquals("rejected", offerRepository.findById(offer1.getId()).orElseThrow().getStatus());

        // Now partner 3 joins the system
        Partner partner3 = createApprovedPartner("Partner 3", "Seattle", "laptop", 10);

        // Expire partner 2's offer
        offer2.setExpiresAt(Instant.now().minusSeconds(10));
        offerRepository.save(offer2);

        // Trigger expiration cleanup
        expirationScheduler.runExpirationCleanup();

        // Check round 2 offers: partner 1 (rejected) and partner 2 (expired) must NOT receive offers; partner 3 should receive an offer
        List<RoutingOffer> allOffers = offerRepository.findAllByPickupId(pickup.getId());
        List<RoutingOffer> round2Offers = allOffers.stream().filter(o -> o.getRound() == 2).toList();
        assertEquals(1, round2Offers.size());
        assertEquals(partner3.getId(), round2Offers.getFirst().getPartnerId());
    }

    @Test
    void maxThreeRoundsCapEscalatesToAdminQueue() {
        // Partner 1 (Round 1)
        Partner partner1 = createApprovedPartner("Partner 1", "Seattle", "laptop", 10);

        Device device = deviceRepository.save(new Device(householdUserId, "good", "laptop", BigDecimal.valueOf(0.95), "completed"));
        ActorContext householdActor = new ActorContext(householdUserId, Role.HOUSEHOLD);

        PickupRequest pickup = pickupService.createPickup(householdActor, new CreatePickup(device.getId(), "Seattle, WA", null));

        // Round 1 offers created
        List<RoutingOffer> r1Offers = offerRepository.findAllByPickupId(pickup.getId());
        assertEquals(1, r1Offers.size());
        assertEquals(1, r1Offers.getFirst().getRound());

        // Add partner 2 before round 1 expires
        Partner partner2 = createApprovedPartner("Partner 2", "Seattle", "laptop", 10);

        // Expire round 1
        r1Offers.getFirst().setExpiresAt(Instant.now().minusSeconds(10));
        offerRepository.save(r1Offers.getFirst());
        expirationScheduler.runExpirationCleanup();

        // Round 2 offers created
        List<RoutingOffer> r2Offers = offerRepository.findAllByPickupId(pickup.getId()).stream()
                .filter(o -> o.getRound() == 2).toList();
        assertEquals(1, r2Offers.size());
        assertEquals(partner2.getId(), r2Offers.getFirst().getPartnerId());

        // Add partner 3 before round 2 expires
        Partner partner3 = createApprovedPartner("Partner 3", "Seattle", "laptop", 10);

        // Expire round 2
        r2Offers.getFirst().setExpiresAt(Instant.now().minusSeconds(10));
        offerRepository.save(r2Offers.getFirst());
        expirationScheduler.runExpirationCleanup();

        // Round 3 offers created
        List<RoutingOffer> r3Offers = offerRepository.findAllByPickupId(pickup.getId()).stream()
                .filter(o -> o.getRound() == 3).toList();
        assertEquals(1, r3Offers.size());
        assertEquals(partner3.getId(), r3Offers.getFirst().getPartnerId());

        // Add partner 4 who would otherwise be eligible if there was a round 4
        Partner partner4 = createApprovedPartner("Partner 4", "Seattle", "laptop", 10);

        // Expire round 3
        r3Offers.getFirst().setExpiresAt(Instant.now().minusSeconds(10));
        offerRepository.save(r3Offers.getFirst());
        expirationScheduler.runExpirationCleanup();

        // Verify: Capped at 3 rounds — NO round 4 offers created
        List<RoutingOffer> r4Offers = offerRepository.findAllByPickupId(pickup.getId()).stream()
                .filter(o -> o.getRound() == 4).toList();
        assertTrue(r4Offers.isEmpty(), "Round 4 offers must NOT be created when capped at 3 rounds");

        // Verify: Admin notified
        List<Notification> adminNotifications = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(adminUserId);
        assertFalse(adminNotifications.isEmpty(), "Admin should receive escalation notification");
        assertEquals("pickup_routing_escalated", adminNotifications.getFirst().getType());

        // Verify: Household notified
        List<Notification> householdNotifications = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(householdUserId);
        assertFalse(householdNotifications.isEmpty(), "Household should receive delay update notification");
        assertEquals("pickup_routing_delayed", householdNotifications.getFirst().getType());

        // Verify: Audit log recorded
        List<AuditLog> auditLogs = auditLogRepository.findAll().stream()
                .filter(a -> "pickup.routing_escalated".equals(a.getAction()))
                .toList();
        assertEquals(1, auditLogs.size());
        assertEquals(pickup.getId(), auditLogs.getFirst().getEntityId());
        assertEquals("ESCALATED", auditLogs.getFirst().getResult());
    }
}
