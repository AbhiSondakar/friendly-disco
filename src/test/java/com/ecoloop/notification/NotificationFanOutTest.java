package com.ecoloop.notification;

import com.ecoloop.identity.NotificationPreference;
import com.ecoloop.identity.NotificationPreferenceRepository;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerApprovedEvent;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.partner.PartnerSuspendedEvent;
import com.ecoloop.pickup.*;
import com.ecoloop.routing.RoutingOffer;
import com.ecoloop.routing.RoutingOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("postgres-test")
class NotificationFanOutTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ecoloop_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    @Autowired UserRepository users;
    @Autowired PartnerRepository partners;
    @Autowired PickupRepository pickups;
    @Autowired RoutingOfferRepository offers;
    @Autowired NotificationRepository notifications;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired TransactionTemplate tx;

    private User household;
    private User partnerUserA;
    private User partnerUserB;
    private User partnerUserC;
    private Partner partnerA;
    private Partner partnerB;
    private Partner partnerC;
    private PickupRequest pickup;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        offers.deleteAll();
        pickups.deleteAll();
        partners.deleteAll();
        users.deleteAllInBatch();

        household = users.saveAndFlush(new User("hh-fanout-" + UUID.randomUUID() + "@ecoloop.test", "hash", "Household", "HOUSEHOLD"));
        partnerUserA = users.saveAndFlush(new User("pa-fanout-" + UUID.randomUUID() + "@ecoloop.test", "hash", "Partner A", "HOUSEHOLD"));
        partnerUserB = users.saveAndFlush(new User("pb-fanout-" + UUID.randomUUID() + "@ecoloop.test", "hash", "Partner B", "HOUSEHOLD"));
        partnerUserC = users.saveAndFlush(new User("pc-fanout-" + UUID.randomUUID() + "@ecoloop.test", "hash", "Partner C", "HOUSEHOLD"));

        partnerA = new Partner(partnerUserA.getId(), "Partner A Org", null, null);
        partnerA.setStatus("approved");
        partnerA = partners.saveAndFlush(partnerA);

        partnerB = new Partner(partnerUserB.getId(), "Partner B Org", null, null);
        partnerB.setStatus("approved");
        partnerB = partners.saveAndFlush(partnerB);

        partnerC = new Partner(partnerUserC.getId(), "Partner C Org", null, null);
        partnerC.setStatus("approved");
        partnerC = partners.saveAndFlush(partnerC);

        pickup = new PickupRequest(household.getId(), null, "123 Test St");
        pickup = pickups.saveAndFlush(pickup);

        Instant expires = Instant.now().plus(2, ChronoUnit.HOURS);
        offers.saveAndFlush(new RoutingOffer(pickup.getId(), partnerA.getId(), expires));
        offers.saveAndFlush(new RoutingOffer(pickup.getId(), partnerB.getId(), expires));
        offers.saveAndFlush(new RoutingOffer(pickup.getId(), partnerC.getId(), expires));
    }

    private void publishAndWait(Runnable publishAction) {
        tx.executeWithoutResult(status -> publishAction.run());
        await().atMost(3, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(() -> notifications.count() > 0);
    }

    @Test
    void pickupCreatedEventSendsOfferReceivedToEachOfferedPartner() {
        publishAndWait(() -> publisher.publishEvent(new PickupCreatedEvent(pickup.getId())));

        List<Notification> aNotifs = notifications.findAllByUserIdOrderByCreatedAtDesc(partnerUserA.getId());
        List<Notification> bNotifs = notifications.findAllByUserIdOrderByCreatedAtDesc(partnerUserB.getId());
        List<Notification> cNotifs = notifications.findAllByUserIdOrderByCreatedAtDesc(partnerUserC.getId());

        assertEquals(1, aNotifs.size());
        assertEquals(1, bNotifs.size());
        assertEquals(1, cNotifs.size());

        assertNotification(aNotifs.get(0), NotificationType.OFFER_RECEIVED, pickup.getId(),
            "New pickup offer available",
            "You have a new pickup offer for a device. Open the app to accept it before it expires.");
    }

    @Test
    void pickupAcceptedEventSendsToHouseholdAndSupersedesOtherOffers() {
        // Publish accepted event where partner A wins
        publishAndWait(() -> publisher.publishEvent(new PickupAcceptedEvent(pickup.getId(), partnerA.getId())));

        List<Notification> hh = notifications.findAllByUserIdOrderByCreatedAtDesc(household.getId());
        assertEquals(1, hh.size());
        assertNotification(hh.get(0), NotificationType.PICKUP_ACCEPTED, pickup.getId(),
            "Pickup accepted",
            "A partner has accepted your pickup request and will be in touch shortly.");

        List<Notification> aNotifs = notifications.findAllByUserIdOrderByCreatedAtDesc(partnerUserA.getId());
        assertEquals(0, aNotifs.size(), "accepting partner must NOT receive superseded notification for own accept");

        List<Notification> bNotifs = notifications.findAllByUserIdOrderByCreatedAtDesc(partnerUserB.getId());
        List<Notification> cNotifs = notifications.findAllByUserIdOrderByCreatedAtDesc(partnerUserC.getId());
        assertEquals(1, bNotifs.size());
        assertEquals(1, cNotifs.size());
        assertNotification(bNotifs.get(0), NotificationType.OFFER_SUPERSEDED, pickup.getId(),
            "Offer no longer available",
            "A pickup offer you received was accepted by another partner and has been withdrawn.");
    }

    @Test
    void pickupRejectedEventSendsPickupReofferedToHousehold() {
        publishAndWait(() -> publisher.publishEvent(new PickupRejectedEvent(pickup.getId(), partnerA.getId())));

        List<Notification> hh = notifications.findAllByUserIdOrderByCreatedAtDesc(household.getId());
        assertEquals(1, hh.size());
        assertNotification(hh.get(0), NotificationType.PICKUP_REOFFERED, pickup.getId(),
            "Pickup needs a new partner",
            "Your pickup was not accepted by the partner. We are finding another one.");
    }

    @Test
    void pickupCancelledEventSendsToAssignedPartner() {
        pickup.setPartnerId(partnerA.getId());
        pickup.setStatus("accepted");
        pickups.saveAndFlush(pickup);

        publishAndWait(() -> publisher.publishEvent(new PickupCancelledEvent(pickup.getId())));

        List<Notification> aNotifs = notifications.findAllByUserIdOrderByCreatedAtDesc(partnerUserA.getId());
        assertEquals(1, aNotifs.size());
        assertNotification(aNotifs.get(0), NotificationType.PICKUP_CANCELLED, pickup.getId(),
            "Pickup cancelled",
            "The pickup you were assigned has been cancelled by the household.");
    }

    @Test
    void pickupVerifiedEventSendsToHousehold() {
        publishAndWait(() -> publisher.publishEvent(new PickupVerifiedEvent(pickup.getId(), partnerA.getId())));

        List<Notification> hh = notifications.findAllByUserIdOrderByCreatedAtDesc(household.getId());
        assertEquals(1, hh.size());
        assertNotification(hh.get(0), NotificationType.PICKUP_VERIFIED, pickup.getId(),
            "Pickup verified",
            "Your pickup has been verified on site by the partner and is moving to completion.");
    }

    @Test
    void pickupCompletedEventSendsToHousehold() {
        publishAndWait(() -> publisher.publishEvent(new PickupCompletedEvent(pickup.getId(), partnerA.getId())));

        List<Notification> hh = notifications.findAllByUserIdOrderByCreatedAtDesc(household.getId());
        assertEquals(1, hh.size());
        assertNotification(hh.get(0), NotificationType.PICKUP_COMPLETED, pickup.getId(),
            "Pickup complete",
            "Your pickup has been completed. Thank you for recycling responsibly.");
    }

    @Test
    void partnerApprovedEventSendsToPartnerUser() {
        publishAndWait(() -> publisher.publishEvent(new PartnerApprovedEvent(partnerA.getId(), partnerUserA.getId())));

        List<Notification> n = notifications.findAllByUserIdOrderByCreatedAtDesc(partnerUserA.getId());
        assertEquals(1, n.size());
        assertNotification(n.get(0), NotificationType.PARTNER_APPROVED, partnerA.getId(),
            "Partner account approved",
            "Great news — your partner application has been approved. You can now accept pickup offers.");
    }

    @Test
    void partnerSuspendedEventSendsToPartnerAndAffectedHouseholds() {
        // assign partner A in accepted state for household so household receives affected notification
        pickup.setPartnerId(partnerA.getId());
        pickup.setStatus("accepted");
        pickups.saveAndFlush(pickup);

        publishAndWait(() -> publisher.publishEvent(
            new PartnerSuspendedEvent(partnerA.getId(), partnerUserA.getId(), List.of(household.getId()))));

        List<Notification> partnerNotif = notifications.findAllByUserIdOrderByCreatedAtDesc(partnerUserA.getId());
        assertEquals(1, partnerNotif.size());
        assertNotification(partnerNotif.get(0), NotificationType.PARTNER_SUSPENDED, partnerA.getId(),
            "Partner account suspended",
            "Your partner account has been suspended. Please contact support for details on next steps.");

        List<Notification> hhNotif = notifications.findAllByUserIdOrderByCreatedAtDesc(household.getId());
        assertEquals(1, hhNotif.size());
        assertNotification(hhNotif.get(0), NotificationType.PARTNER_CHANGED, partnerA.getId(),
            "Partner status change",
            "One of the partners on your account has changed status. Any active jobs may be reassigned.");
    }

    private static void assertNotification(Notification n,
                                           NotificationType expectedType,
                                           UUID expectedRef,
                                           String expectedTitle,
                                           String expectedBody) {
        assertEquals(expectedType.value(), n.getType());
        assertEquals(expectedRef, n.getReferenceId());
        assertEquals(expectedTitle, n.getTitle());
        assertEquals(expectedBody, n.getBody());
        assertFalse(n.isRead());
    }
}
