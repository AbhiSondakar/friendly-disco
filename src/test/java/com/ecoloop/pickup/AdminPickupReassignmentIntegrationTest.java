package com.ecoloop.pickup;

import com.ecoloop.device.Device;
import com.ecoloop.device.DeviceRepository;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserPrincipal;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.notification.Notification;
import com.ecoloop.notification.NotificationRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminPickupReassignmentIntegrationTest {

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
    private NotificationRepository notificationRepository;

    private User adminUser;
    private User householdUser;
    private User partner1User;
    private User partner2User;
    private Partner partner1;
    private Partner partner2;
    private PickupRequest pickup;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(new User(
            "admin." + UUID.randomUUID() + "@example.com", "hash", "Admin User", "ADMIN"
        ));

        householdUser = userRepository.save(new User(
            "household." + UUID.randomUUID() + "@example.com", "hash", "Household User", "HOUSEHOLD"
        ));

        Device device = deviceRepository.save(new Device(
            householdUser.getId(), "good", "laptop", BigDecimal.valueOf(0.8), "completed"
        ));

        partner1User = userRepository.save(new User(
            "partner1." + UUID.randomUUID() + "@example.com", "hash", "Partner One", "PARTNER"
        ));
        partner1 = new Partner(partner1User.getId(), "Partner One Org", "Recycler", "LIC-P1");
        partner1.setStatus("approved");
        partner1.setCapacity(5);
        partner1 = partnerRepository.save(partner1);

        partner2User = userRepository.save(new User(
            "partner2." + UUID.randomUUID() + "@example.com", "hash", "Partner Two", "PARTNER"
        ));
        partner2 = new Partner(partner2User.getId(), "Partner Two Org", "Recycler", "LIC-P2");
        partner2.setStatus("approved");
        partner2.setCapacity(5);
        partner2 = partnerRepository.save(partner2);

        pickup = new PickupRequest(householdUser.getId(), device.getId(), "100 Eco Blvd");
        pickup.setStatus("accepted");
        pickup.setPartnerId(partner1.getId());
        pickup = pickupRepository.save(pickup);
    }

    @Test
    void anonymousRequestReturns401() throws Exception {
        String payload = "{\"newPartnerId\":\"" + partner2.getId() + "\"}";

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void householdRequestReturns403() throws Exception {
        String payload = "{\"newPartnerId\":\"" + partner2.getId() + "\"}";

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(householdUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isForbidden());
    }

    @Test
    void partnerRequestReturns403() throws Exception {
        String payload = "{\"newPartnerId\":\"" + partner2.getId() + "\"}";

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(partner1User)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminWithMissingBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminWithNullNewPartnerIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPartnerId\":null}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminWithEmptyJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminWithNonExistentPartnerReturns404() throws Exception {
        UUID randomPartnerId = UUID.randomUUID();
        String payload = "{\"newPartnerId\":\"" + randomPartnerId + "\"}";

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminWithUnapprovedPartnerReturns409() throws Exception {
        Partner pendingPartner = new Partner(UUID.randomUUID(), "Pending Partner", "Recycler", "LIC-PND");
        pendingPartner.setStatus("pending");
        pendingPartner = partnerRepository.save(pendingPartner);

        String payload = "{\"newPartnerId\":\"" + pendingPartner.getId() + "\"}";

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isConflict());

        Partner suspendedPartner = new Partner(UUID.randomUUID(), "Suspended Partner", "Recycler", "LIC-SUS");
        suspendedPartner.setStatus("suspended");
        suspendedPartner = partnerRepository.save(suspendedPartner);

        String payload2 = "{\"newPartnerId\":\"" + suspendedPartner.getId() + "\"}";

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload2))
            .andExpect(status().isConflict());
    }

    @Test
    void adminWithAtCapacityPartnerReturns409() throws Exception {
        Partner fullPartner = new Partner(UUID.randomUUID(), "Full Partner", "Recycler", "LIC-FULL");
        fullPartner.setStatus("approved");
        fullPartner.setCapacity(1);
        fullPartner = partnerRepository.save(fullPartner);

        PickupRequest activeJob = new PickupRequest(householdUser.getId(), pickup.getDeviceId(), "456 Oak St");
        activeJob.setStatus("accepted");
        activeJob.setPartnerId(fullPartner.getId());
        pickupRepository.save(activeJob);

        String payload = "{\"newPartnerId\":\"" + fullPartner.getId() + "\"}";

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isConflict());
    }

    @Test
    void adminWithCompletedOrCancelledPickupReturns409() throws Exception {
        pickup.setStatus("completed");
        pickupRepository.save(pickup);

        String payload = "{\"newPartnerId\":\"" + partner2.getId() + "\"}";

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isConflict());

        pickup.setStatus("cancelled");
        pickupRepository.save(pickup);

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isConflict());
    }

    @Test
    void adminSuccessfulReassignmentWithNewPartnerIdField() throws Exception {
        AdminPickupController.ReassignRequest body = new AdminPickupController.ReassignRequest(partner2.getId());

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(pickup.getId().toString()))
            .andExpect(jsonPath("$.status").value("accepted"))
            .andExpect(jsonPath("$.partnerId").value(partner2.getId().toString()));

        PickupRequest reloaded = pickupRepository.findById(pickup.getId()).orElseThrow();
        assertEquals(partner2.getId(), reloaded.getPartnerId());
        assertEquals("accepted", reloaded.getStatus());

        List<Notification> householdNotifs = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(householdUser.getId());
        assertFalse(householdNotifs.isEmpty());
        assertTrue(householdNotifs.stream().anyMatch(n -> "pickup_reassigned".equals(n.getType())));

        List<Notification> prevPartnerNotifs = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(partner1User.getId());
        assertFalse(prevPartnerNotifs.isEmpty());
        assertTrue(prevPartnerNotifs.stream().anyMatch(n -> "pickup_reassigned".equals(n.getType())));
    }

    @Test
    void adminSuccessfulReassignmentWithPartnerIdAlias() throws Exception {
        String legacyJson = "{\"partnerId\":\"" + partner2.getId() + "\"}";

        mockMvc.perform(post("/api/admin/pickups/{id}/reassign", pickup.getId())
                .with(user(UserPrincipal.from(adminUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(legacyJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(pickup.getId().toString()))
            .andExpect(jsonPath("$.status").value("accepted"))
            .andExpect(jsonPath("$.partnerId").value(partner2.getId().toString()));

        PickupRequest reloaded = pickupRepository.findById(pickup.getId()).orElseThrow();
        assertEquals(partner2.getId(), reloaded.getPartnerId());
    }
}
