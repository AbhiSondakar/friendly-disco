package com.ecoloop.pickup;

import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.security.Role;
import com.ecoloop.device.Device;
import com.ecoloop.device.DeviceRepository;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserPrincipal;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.routing.RoutingOffer;
import com.ecoloop.routing.RoutingOfferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthorizationMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private UserRepository userRepository;

    private User householdUser;
    private User partnerUser;
    private User otherPartnerUser;
    private User adminUser;

    private Partner partner;
    private Partner otherPartner;
    private Partner unapprovedPartner;
    private Partner atCapacityPartner;

    private Device householdDevice;

    @BeforeEach
    void setUp() {
        householdUser = userRepository.save(new User(
            "household." + UUID.randomUUID() + "@ecoloop.test", "hash", "Household User", Role.HOUSEHOLD.name()
        ));
        householdUser.setEmailVerified(true);
        householdUser = userRepository.save(householdUser);

        partnerUser = userRepository.save(new User(
            "partner." + UUID.randomUUID() + "@ecoloop.test", "hash", "Partner User", Role.PARTNER.name()
        ));
        partnerUser.setEmailVerified(true);
        partnerUser = userRepository.save(partnerUser);

        otherPartnerUser = userRepository.save(new User(
            "other_partner." + UUID.randomUUID() + "@ecoloop.test", "hash", "Other Partner User", Role.PARTNER.name()
        ));
        otherPartnerUser.setEmailVerified(true);
        otherPartnerUser = userRepository.save(otherPartnerUser);

        adminUser = userRepository.save(new User(
            "admin." + UUID.randomUUID() + "@ecoloop.test", "hash", "Admin User", Role.ADMIN.name()
        ));
        adminUser.setEmailVerified(true);
        adminUser = userRepository.save(adminUser);

        partner = new Partner(partnerUser.getId(), "Primary Partner", "Recycler", "LIC-P1");
        partner.setStatus("approved");
        partner.setCapacity(10);
        partner.setRating(BigDecimal.valueOf(4.9));
        partner = partnerRepository.save(partner);

        otherPartner = new Partner(otherPartnerUser.getId(), "Other Partner", "Recycler", "LIC-P2");
        otherPartner.setStatus("approved");
        otherPartner.setCapacity(10);
        otherPartner.setRating(BigDecimal.valueOf(4.5));
        otherPartner = partnerRepository.save(otherPartner);

        unapprovedPartner = new Partner(UUID.randomUUID(), "Pending Partner", "Recycler", "LIC-P3");
        unapprovedPartner.setStatus("pending");
        unapprovedPartner.setCapacity(5);
        unapprovedPartner = partnerRepository.save(unapprovedPartner);

        atCapacityPartner = new Partner(UUID.randomUUID(), "Full Partner", "Recycler", "LIC-P4");
        atCapacityPartner.setStatus("approved");
        atCapacityPartner.setCapacity(0);
        atCapacityPartner = partnerRepository.save(atCapacityPartner);

        householdDevice = deviceRepository.save(new Device(
            householdUser.getId(), "good", "laptop", BigDecimal.valueOf(0.8), "ready_for_pickup"
        ));
    }

    private PickupRequest createPickup(String status, UUID assignedPartnerId) {
        PickupRequest p = new PickupRequest(householdUser.getId(), householdDevice.getId(), "100 Green St");
        p.setStatus(status);
        p.setPartnerId(assignedPartnerId);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return pickupRepository.save(p);
    }

    private RoutingOffer createOffer(UUID pickupId, UUID partnerId, Instant expiresAt, String status) {
        RoutingOffer offer = new RoutingOffer(pickupId, partnerId, expiresAt);
        offer.setStatus(status);
        return offerRepository.save(offer);
    }

    // =========================================================================
    // Explicit cases required by Phase 1F
    // =========================================================================

    @Test
    @DisplayName("PARTNER accepts pending WITHOUT offer -> 403")
    void partnerAcceptsPendingWithoutOffer_returns403() {
        PickupRequest pickup = createPickup("pending", null);
        ActorContext partnerActor = new ActorContext(partnerUser.getId(), Role.PARTNER);

        // Missing offer ID entirely
        assertThrows(AccessDeniedException.class, () ->
            pickupService.acceptOfferedPickup(partnerActor, pickup.getId(), null));

        // Random non-existent offer ID
        assertThrows(AccessDeniedException.class, () ->
            pickupService.acceptOfferedPickup(partnerActor, pickup.getId(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("PARTNER accepts pending WITH expired offer -> 409")
    void partnerAcceptsPendingWithExpiredOffer_returns409() throws Exception {
        PickupRequest pickup = createPickup("pending", null);
        RoutingOffer expiredOffer = createOffer(pickup.getId(), partner.getId(),
            Instant.now().minus(1, ChronoUnit.HOURS), "offered");

        mockMvc.perform(post("/api/pickups/{id}/accept", pickup.getId())
                .with(user(UserPrincipal.from(partnerUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AcceptRequest(expiredOffer.getId()))))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PARTNER accepts pending WITH another partner offer -> 403")
    void partnerAcceptsPendingWithAnotherPartnerOffer_returns403() throws Exception {
        PickupRequest pickup = createPickup("pending", null);
        RoutingOffer otherOffer = createOffer(pickup.getId(), otherPartner.getId(),
            Instant.now().plus(1, ChronoUnit.HOURS), "offered");

        mockMvc.perform(post("/api/pickups/{id}/accept", pickup.getId())
                .with(user(UserPrincipal.from(partnerUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AcceptRequest(otherOffer.getId()))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HOUSEHOLD calls complete via any endpoint -> 403")
    void householdCallsCompleteViaAnyEndpoint_returns403() throws Exception {
        PickupRequest pickup = createPickup("accepted", partner.getId());
        RoutingOffer offer = createOffer(pickup.getId(), partner.getId(),
            Instant.now().plus(1, ChronoUnit.HOURS), "accepted");

        // /api/pickups/{id}/complete
        mockMvc.perform(post("/api/pickups/{id}/complete", pickup.getId())
                .with(user(UserPrincipal.from(householdUser)))
                .with(csrf()))
            .andExpect(status().isForbidden());

        // /api/routing/offers/{id}/complete
        mockMvc.perform(post("/api/routing/offers/{id}/complete", offer.getId())
                .with(user(UserPrincipal.from(householdUser)))
                .with(csrf()))
            .andExpect(status().isForbidden());

        // /api/partners/jobs/{id}/complete
        mockMvc.perform(post("/api/partners/jobs/{id}/complete", pickup.getId())
                .with(user(UserPrincipal.from(householdUser)))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HOUSEHOLD cancels verified via /pickups/{id}/cancel -> 409")
    void householdCancelsVerifiedViaPickupsCancel_returns409() throws Exception {
        PickupRequest pickup = createPickup("verified", partner.getId());

        mockMvc.perform(post("/api/pickups/{id}/cancel", pickup.getId())
                .with(user(UserPrincipal.from(householdUser)))
                .with(csrf()))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HOUSEHOLD cancels verified via /devices/{id}/cancel-pickup -> 409")
    void householdCancelsVerifiedViaDevicesCancelPickup_returns409() throws Exception {
        PickupRequest pickup = createPickup("verified", partner.getId());

        mockMvc.perform(post("/api/devices/{id}/cancel-pickup", householdDevice.getId())
                .with(user(UserPrincipal.from(householdUser)))
                .with(csrf()))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("ADMIN reassigns to unapproved partner -> 409")
    void adminReassignsToUnapprovedPartner_returns409() throws Exception {
        PickupRequest pickup = createPickup("accepted", partner.getId());

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("newPartnerId", unapprovedPartner.getId()))))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("ADMIN reassigns to at-capacity partner -> 409")
    void adminReassignsToAtCapacityPartner_returns409() throws Exception {
        PickupRequest pickup = createPickup("accepted", partner.getId());

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("newPartnerId", atCapacityPartner.getId()))))
            .andExpect(status().isConflict());
    }

    // =========================================================================
    // Parameterized Matrix Tests: (Role, Action, StartStatus) -> Expected Status
    // =========================================================================

    static Stream<Arguments> matrixTestCases() {
        return Stream.of(
            // CANCEL: Household allowed on pending/accepted; 409 on verified/completed/cancelled
            Arguments.of(Role.HOUSEHOLD, "CANCEL", "pending", 200),
            Arguments.of(Role.HOUSEHOLD, "CANCEL", "accepted", 200),
            Arguments.of(Role.HOUSEHOLD, "CANCEL", "verified", 409),
            Arguments.of(Role.HOUSEHOLD, "CANCEL", "completed", 409),
            Arguments.of(Role.HOUSEHOLD, "CANCEL", "cancelled", 409),

            // CANCEL: Partner forbidden on all
            Arguments.of(Role.PARTNER, "CANCEL", "pending", 403),
            Arguments.of(Role.PARTNER, "CANCEL", "accepted", 403),
            Arguments.of(Role.PARTNER, "CANCEL", "verified", 403),

            // ACCEPT: Household and Admin forbidden
            Arguments.of(Role.HOUSEHOLD, "ACCEPT", "pending", 403),
            Arguments.of(Role.ADMIN, "ACCEPT", "pending", 403),

            // ACCEPT: Partner on non-pending returns 409
            Arguments.of(Role.PARTNER, "ACCEPT", "accepted", 409),
            Arguments.of(Role.PARTNER, "ACCEPT", "verified", 409),
            Arguments.of(Role.PARTNER, "ACCEPT", "completed", 409),
            Arguments.of(Role.PARTNER, "ACCEPT", "cancelled", 409),

            // REJECT: Household and Admin forbidden
            Arguments.of(Role.HOUSEHOLD, "REJECT", "accepted", 403),
            Arguments.of(Role.ADMIN, "REJECT", "accepted", 403),

            // REJECT: Partner on accepted returns 200; on pending (unassigned) returns 403; on verified/completed returns 409
            Arguments.of(Role.PARTNER, "REJECT", "accepted", 200),
            Arguments.of(Role.PARTNER, "REJECT", "pending", 403),
            Arguments.of(Role.PARTNER, "REJECT", "verified", 409),
            Arguments.of(Role.PARTNER, "REJECT", "completed", 409),

            // COMPLETE: Household and Admin forbidden
            Arguments.of(Role.HOUSEHOLD, "COMPLETE", "accepted", 403),
            Arguments.of(Role.HOUSEHOLD, "COMPLETE", "verified", 403),
            Arguments.of(Role.ADMIN, "COMPLETE", "accepted", 403),
            Arguments.of(Role.ADMIN, "COMPLETE", "verified", 403),

            // COMPLETE: Partner allowed on accepted/verified; 403 on pending (unassigned); 409 on cancelled
            Arguments.of(Role.PARTNER, "COMPLETE", "accepted", 200),
            Arguments.of(Role.PARTNER, "COMPLETE", "verified", 200),
            Arguments.of(Role.PARTNER, "COMPLETE", "pending", 403),
            Arguments.of(Role.PARTNER, "COMPLETE", "cancelled", 409),

            // REASSIGN: Household and Partner forbidden
            Arguments.of(Role.HOUSEHOLD, "REASSIGN", "accepted", 403),
            Arguments.of(Role.PARTNER, "REASSIGN", "accepted", 403),

            // REASSIGN: Admin allowed on pending/accepted/verified; 409 on completed/cancelled
            Arguments.of(Role.ADMIN, "REASSIGN", "pending", 200),
            Arguments.of(Role.ADMIN, "REASSIGN", "accepted", 200),
            Arguments.of(Role.ADMIN, "REASSIGN", "verified", 200),
            Arguments.of(Role.ADMIN, "REASSIGN", "completed", 409),
            Arguments.of(Role.ADMIN, "REASSIGN", "cancelled", 409)
        );
    }

    @ParameterizedTest(name = "Role {0} performing {1} on {2} pickup expects HTTP {3}")
    @MethodSource("matrixTestCases")
    void testAuthorizationMatrix(Role role, String action, String startStatus, int expectedStatus) throws Exception {
        UUID assignedPartnerId = "pending".equals(startStatus) ? null : partner.getId();
        PickupRequest pickup = createPickup(startStatus, assignedPartnerId);

        User actingUser = switch (role) {
            case HOUSEHOLD -> householdUser;
            case PARTNER -> partnerUser;
            case ADMIN -> adminUser;
        };

        switch (action) {
            case "CANCEL" -> {
                mockMvc.perform(post("/api/pickups/{id}/cancel", pickup.getId())
                        .with(user(UserPrincipal.from(actingUser)))
                        .with(csrf()))
                    .andExpect(status().is(expectedStatus));
            }
            case "ACCEPT" -> {
                RoutingOffer offer = createOffer(pickup.getId(), partner.getId(),
                    Instant.now().plus(1, ChronoUnit.HOURS), "offered");
                mockMvc.perform(post("/api/pickups/{id}/accept", pickup.getId())
                        .with(user(UserPrincipal.from(actingUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptRequest(offer.getId()))))
                    .andExpect(status().is(expectedStatus));
            }
            case "REJECT" -> {
                mockMvc.perform(post("/api/pickups/{id}/reject", pickup.getId())
                        .with(user(UserPrincipal.from(actingUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Vehicle breakdown"))))
                    .andExpect(status().is(expectedStatus));
            }
            case "COMPLETE" -> {
                mockMvc.perform(post("/api/pickups/{id}/complete", pickup.getId())
                        .with(user(UserPrincipal.from(actingUser)))
                        .with(csrf()))
                    .andExpect(status().is(expectedStatus));
            }
            case "REASSIGN" -> {
                mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                        .with(user(UserPrincipal.from(actingUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPartnerId", otherPartner.getId()))))
                    .andExpect(status().is(expectedStatus));
            }
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
    }
}
