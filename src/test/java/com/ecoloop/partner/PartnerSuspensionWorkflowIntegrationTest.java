package com.ecoloop.partner;

import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.notification.NotificationRepository;
import com.ecoloop.pickup.PickupRepository;
import com.ecoloop.pickup.PickupRequest;
import com.ecoloop.routing.RoutingOffer;
import com.ecoloop.routing.RoutingOfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PartnerSuspensionWorkflowIntegrationTest {

    @Autowired PartnerLifecycleService lifecycle;
    @Autowired UserRepository users;
    @Autowired PartnerRepository partners;
    @Autowired PickupRepository pickups;
    @Autowired RoutingOfferRepository offers;
    @Autowired NotificationRepository notifications;

    @Test
    void approvalPromotesPendingPartnerUserToPartnerRole() {
        User applicant = users.save(new User(email("applicant"), "hash", "Applicant", "HOUSEHOLD"));
        Partner pendingPartner = partners.save(new Partner(
            applicant.getId(), "Pending Org", "Recycler", "LIC-" + UUID.randomUUID()));

        Partner approvedPartner = lifecycle.approve(pendingPartner.getId());

        assertEquals("approved", approvedPartner.getStatus());
        assertEquals("PARTNER", users.findById(applicant.getId()).orElseThrow().getRole());
    }

    @Test
    void suspensionCancelsActiveJobsAndEveryRelatedOfferBeforeNotifyingParties() {
        User household = users.save(new User(email("household"), "hash", "Household", "HOUSEHOLD"));
        User suspendedPartnerUser = users.save(new User(email("suspended"), "hash", "Partner", "PARTNER"));
        User otherPartnerUser = users.save(new User(email("other"), "hash", "Other partner", "PARTNER"));

        Partner suspendedPartner = approvedPartner(suspendedPartnerUser, "Suspended org");
        Partner otherPartner = approvedPartner(otherPartnerUser, "Other org");

        PickupRequest pickup = new PickupRequest(household.getId(), null, "1 Recycling Way");
        pickup.setStatus("accepted");
        pickup.setPartnerId(suspendedPartner.getId());
        pickup = pickups.save(pickup);

        RoutingOffer acceptedOffer = new RoutingOffer(pickup.getId(), suspendedPartner.getId(), Instant.now().plusSeconds(3600));
        acceptedOffer.setStatus("accepted");
        acceptedOffer = offers.save(acceptedOffer);
        RoutingOffer offeredOffer = offers.save(new RoutingOffer(
            pickup.getId(), otherPartner.getId(), Instant.now().plusSeconds(3600)));

        lifecycle.suspend(suspendedPartner.getId());

        assertEquals("suspended", partners.findById(suspendedPartner.getId()).orElseThrow().getStatus());
        assertEquals("HOUSEHOLD", users.findById(suspendedPartnerUser.getId()).orElseThrow().getRole());
        assertEquals("cancelled", pickups.findById(pickup.getId()).orElseThrow().getStatus());
        assertEquals("cancelled", offers.findById(acceptedOffer.getId()).orElseThrow().getStatus());
        assertEquals("cancelled", offers.findById(offeredOffer.getId()).orElseThrow().getStatus());

        assertTrue(notifications.findAllByUserIdOrderByCreatedAtDesc(household.getId()).stream()
            .anyMatch(notification -> "partner_changed".equals(notification.getType())));
        assertTrue(notifications.findAllByUserIdOrderByCreatedAtDesc(suspendedPartnerUser.getId()).stream()
            .anyMatch(notification -> "partner_suspended".equals(notification.getType())));
        assertTrue(notifications.findAllByUserIdOrderByCreatedAtDesc(suspendedPartnerUser.getId()).stream()
            .anyMatch(notification -> "pickup_cancelled".equals(notification.getType())));
        assertTrue(notifications.findAllByUserIdOrderByCreatedAtDesc(otherPartnerUser.getId()).stream()
            .anyMatch(notification -> "pickup_cancelled".equals(notification.getType())));
    }

    private Partner approvedPartner(User user, String organization) {
        Partner partner = new Partner(user.getId(), organization, "Recycler", "LIC-" + UUID.randomUUID());
        partner.setStatus("approved");
        return partners.save(partner);
    }

    private String email(String prefix) {
        return prefix + "." + UUID.randomUUID() + "@ecoloop.test";
    }
}
