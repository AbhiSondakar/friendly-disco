package com.ecoloop.pickup;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class PickupOfferAcceptanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PickupRepository pickupRepository;

    @Autowired
    private RoutingOfferRepository offerRepository;

    private User partner1User;
    private User partner2User;
    private Partner partner1;
    private Partner partner2;
    private PickupRequest pickup;

    @BeforeEach
    void setUp() {
        UUID householdId = UUID.randomUUID();
        User householdUser = userRepository.save(new User(
            "household." + householdId + "@example.com", "hash", "Household User", "HOUSEHOLD"
        ));

        Device device = deviceRepository.save(new Device(
            householdUser.getId(), "good", "laptop", BigDecimal.valueOf(0.8), "completed"
        ));

        pickup = new PickupRequest(householdUser.getId(), device.getId(), "100 Green St");
        pickup.setStatus("pending");
        pickup = pickupRepository.save(pickup);

        UUID p1Id = UUID.randomUUID();
        partner1User = userRepository.save(new User(
            "partner1." + p1Id + "@example.com", "hash", "Partner One", "PARTNER"
        ));
        partner1 = new Partner(partner1User.getId(), "Partner One Org", "Recycler", "LIC-P1");
        partner1.setStatus("approved");
        partner1.setCapacity(5);
        partner1 = partnerRepository.save(partner1);

        UUID p2Id = UUID.randomUUID();
        partner2User = userRepository.save(new User(
            "partner2." + p2Id + "@example.com", "hash", "Partner Two", "PARTNER"
        ));
        partner2 = new Partner(partner2User.getId(), "Partner Two Org", "Recycler", "LIC-P2");
        partner2.setStatus("approved");
        partner2.setCapacity(5);
        partner2 = partnerRepository.save(partner2);
    }

    @Test
    void partnerWithoutOfferGets403OnPickupAccept() throws Exception {
        UUID randomOfferId = UUID.randomUUID();
        AcceptRequest body = new AcceptRequest(randomOfferId);

        mockMvc.perform(post("/api/pickups/{id}/accept", pickup.getId())
                .with(user(UserPrincipal.from(partner1User)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    void partnerWithoutOfferGets403OnOfferAcceptAliases() throws Exception {
        UUID randomOfferId = UUID.randomUUID();

        mockMvc.perform(post("/api/partners/offers/{id}/accept", randomOfferId)
                .with(user(UserPrincipal.from(partner1User)))
                .with(csrf()))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/routing/offers/{id}/accept", randomOfferId)
                .with(user(UserPrincipal.from(partner1User)))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void partnerWithExpiredOfferGets409OnPickupAccept() throws Exception {
        RoutingOffer expiredOffer = new RoutingOffer(pickup.getId(), partner1.getId(), Instant.now().minusSeconds(60));
        expiredOffer.setStatus("offered");
        expiredOffer = offerRepository.save(expiredOffer);

        AcceptRequest body = new AcceptRequest(expiredOffer.getId());

        mockMvc.perform(post("/api/pickups/{id}/accept", pickup.getId())
                .with(user(UserPrincipal.from(partner1User)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict());
    }

    @Test
    void partnerWithExpiredOfferGets409OnOfferAcceptAlias() throws Exception {
        RoutingOffer expiredOffer = new RoutingOffer(pickup.getId(), partner1.getId(), Instant.now().minusSeconds(60));
        expiredOffer.setStatus("offered");
        expiredOffer = offerRepository.save(expiredOffer);

        mockMvc.perform(post("/api/partners/offers/{id}/accept", expiredOffer.getId())
                .with(user(UserPrincipal.from(partner1User)))
                .with(csrf()))
            .andExpect(status().isConflict());
    }

    @Test
    void partnerWithAnotherPartnersOfferGets403OnPickupAccept() throws Exception {
        RoutingOffer otherOffer = new RoutingOffer(pickup.getId(), partner2.getId(), Instant.now().plusSeconds(3600));
        otherOffer.setStatus("offered");
        otherOffer = offerRepository.save(otherOffer);

        AcceptRequest body = new AcceptRequest(otherOffer.getId());

        mockMvc.perform(post("/api/pickups/{id}/accept", pickup.getId())
                .with(user(UserPrincipal.from(partner1User)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    void partnerWithAnotherPartnersOfferGets403OnOfferAcceptAlias() throws Exception {
        RoutingOffer otherOffer = new RoutingOffer(pickup.getId(), partner2.getId(), Instant.now().plusSeconds(3600));
        otherOffer.setStatus("offered");
        otherOffer = offerRepository.save(otherOffer);

        mockMvc.perform(post("/api/partners/offers/{id}/accept", otherOffer.getId())
                .with(user(UserPrincipal.from(partner1User)))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void partnerWithValidOfferGets200AndAcceptsPickup() throws Exception {
        RoutingOffer validOffer = new RoutingOffer(pickup.getId(), partner1.getId(), Instant.now().plusSeconds(3600));
        validOffer.setStatus("offered");
        validOffer = offerRepository.save(validOffer);

        AcceptRequest body = new AcceptRequest(validOffer.getId());

        mockMvc.perform(post("/api/pickups/{id}/accept", pickup.getId())
                .with(user(UserPrincipal.from(partner1User)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk());

        PickupRequest updatedPickup = pickupRepository.findById(pickup.getId()).orElseThrow();
        assertEquals("accepted", updatedPickup.getStatus());
        assertEquals(partner1.getId(), updatedPickup.getPartnerId());

        RoutingOffer updatedOffer = offerRepository.findById(validOffer.getId()).orElseThrow();
        assertEquals("accepted", updatedOffer.getStatus());
    }

    @Test
    void partnerWithValidOfferGets200OnOfferAcceptAlias() throws Exception {
        RoutingOffer validOffer = new RoutingOffer(pickup.getId(), partner1.getId(), Instant.now().plusSeconds(3600));
        validOffer.setStatus("offered");
        validOffer = offerRepository.save(validOffer);

        mockMvc.perform(post("/api/partners/offers/{id}/accept", validOffer.getId())
                .with(user(UserPrincipal.from(partner1User)))
                .with(csrf()))
            .andExpect(status().isOk());

        PickupRequest updatedPickup = pickupRepository.findById(pickup.getId()).orElseThrow();
        assertEquals("accepted", updatedPickup.getStatus());
        assertEquals(partner1.getId(), updatedPickup.getPartnerId());

        RoutingOffer updatedOffer = offerRepository.findById(validOffer.getId()).orElseThrow();
        assertEquals("accepted", updatedOffer.getStatus());
    }
}
