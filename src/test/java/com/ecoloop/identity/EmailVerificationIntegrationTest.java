package com.ecoloop.identity;

import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerController;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.rewards.RewardCatalogItem;
import com.ecoloop.rewards.RewardCatalogRepository;
import com.ecoloop.rewards.RewardLedger;
import com.ecoloop.rewards.RewardLedgerRepository;
import com.ecoloop.rewards.RewardsController;
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
@Transactional
public class EmailVerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private RewardCatalogRepository catalogRepository;

    @Autowired
    private RewardLedgerRepository ledgerRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    private User unverifiedUser;
    private RewardCatalogItem catalogItem;

    @BeforeEach
    void setUp() {
        unverifiedUser = new User(
            "unverified." + UUID.randomUUID() + "@example.com",
            "$2a$10$abcdefghijklmnopqrstuvwxyz1234567890",
            "Unverified User",
            "HOUSEHOLD"
        );
        unverifiedUser.setEmailVerified(false);
        unverifiedUser = userRepository.save(unverifiedUser);

        catalogItem = new RewardCatalogItem("10% Discount", "Discount voucher", 100);
        catalogItem.setActive(true);
        catalogItem = catalogRepository.save(catalogItem);

        ledgerRepository.save(new RewardLedger(unverifiedUser.getId(), 500, "earn", "Initial points", UUID.randomUUID()));
    }

    @Test
    void unverifiedUserCannotRegisterAsPartner() throws Exception {
        PartnerController.Registration reg = new PartnerController.Registration(
            "Green Recyclers", "Recycler", "LIC-999", "North", "Electronics"
        );

        mockMvc.perform(post("/api/partners")
                .with(user(UserPrincipal.from(unverifiedUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
            .andExpect(status().isConflict());
    }

    @Test
    void unverifiedUserCannotRedeemRewards() throws Exception {
        RewardsController.RedemptionRequest req = new RewardsController.RedemptionRequest(
            catalogItem.getId().toString(), 100
        );

        mockMvc.perform(post("/api/rewards/redeem")
                .with(user(UserPrincipal.from(unverifiedUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict());
    }

    @Test
    void userRegistrationCreatesUnverifiedUserAndDispatchesToken() throws Exception {
        String email = "fresh." + UUID.randomUUID() + "@example.com";
        AuthController.RegisterRequest req = new AuthController.RegisterRequest(
            email, "ValidP@ssword123!", "Fresh User", "1234567890", "123 Green Lane"
        );

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(email));

        User freshUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertFalse(freshUser.isEmailVerified());

        // Token should be created
        var tokens = tokenRepository.findAll().stream()
            .filter(t -> t.getUserId().equals(freshUser.getId()))
            .toList();
        assertFalse(tokens.isEmpty());
    }

    @Test
    void emailVerificationEnablesPartnerRegistrationAndRewardRedemption() throws Exception {
        // Send verification email to unverified user
        emailVerificationService.sendVerificationEmail(unverifiedUser);

        EmailVerificationToken token = tokenRepository.findAll().stream()
            .filter(t -> t.getUserId().equals(unverifiedUser.getId()))
            .findFirst()
            .orElseThrow();

        // Directly verify email on the user
        unverifiedUser.setEmailVerified(true);
        userRepository.save(unverifiedUser);

        // Now partner registration succeeds
        PartnerController.Registration reg = new PartnerController.Registration(
            "Verified Recyclers", "Recycler", "LIC-VERIFIED", "North", "Electronics"
        );

        mockMvc.perform(post("/api/partners")
                .with(user(UserPrincipal.from(unverifiedUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
            .andExpect(status().isOk());

        // Now reward redemption succeeds
        RewardsController.RedemptionRequest req = new RewardsController.RedemptionRequest(
            catalogItem.getId().toString(), 100
        );

        mockMvc.perform(post("/api/rewards/redeem")
                .with(user(UserPrincipal.from(unverifiedUser)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());
    }

    @Test
    void invalidTokenReturnsBadRequest() throws Exception {
        AuthController.VerifyEmailRequest req = new AuthController.VerifyEmailRequest("invalid.token");

        mockMvc.perform(post("/api/auth/verify-email")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }
}
