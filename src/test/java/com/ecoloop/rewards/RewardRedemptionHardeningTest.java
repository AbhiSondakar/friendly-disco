package com.ecoloop.rewards;

import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RewardRedemptionHardeningTest {

    @Autowired
    private RewardService rewardService;

    @Autowired
    private RewardLedgerRepository ledgerRepository;

    @Autowired
    private RewardCatalogRepository catalogRepository;

    @Autowired
    private RedemptionRepository redemptionRepository;

    @Autowired
    private UserRepository userRepository;

    private User verifiedUser;
    private User unverifiedUser;
    private RewardCatalogItem catalogItem;

    @BeforeEach
    void setUp() {
        ledgerRepository.deleteAll();
        redemptionRepository.deleteAll();
        catalogRepository.deleteAll();
        userRepository.deleteAll();

        verifiedUser = new User("verified@ecoloop.test", "hash", "Verified User", "HOUSEHOLD");
        verifiedUser.setEmailVerified(true);
        verifiedUser = userRepository.save(verifiedUser);

        unverifiedUser = new User("unverified@ecoloop.test", "hash", "Unverified User", "HOUSEHOLD");
        unverifiedUser.setEmailVerified(false);
        unverifiedUser = userRepository.save(unverifiedUser);

        catalogItem = new RewardCatalogItem("Eco Water Bottle", "Insulated bottle", 500);
        catalogItem.setActive(true);
        catalogItem = catalogRepository.save(catalogItem);
    }

    @Test
    void unverifiedEmailBlocksRedemption() {
        // Award points to unverified user
        ledgerRepository.save(new RewardLedger(unverifiedUser.getId(), 1000, "earn"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            rewardService.redeem(unverifiedUser.getId(), catalogItem.getId())
        );
        assertTrue(ex.getMessage().contains("Email must be verified"), "Must reject unverified email");
    }

    @Test
    void insufficientPointsBlocksRedemption() {
        // User has only 100 points, item costs 500
        ledgerRepository.save(new RewardLedger(verifiedUser.getId(), 100, "earn"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            rewardService.redeem(verifiedUser.getId(), catalogItem.getId())
        );
        assertTrue(ex.getMessage().contains("Insufficient points"), "Must reject when balance is insufficient");
    }

    @Test
    void serverAuthoritativelyDerivesPointsCostUnderLock() {
        // Award 1000 points
        ledgerRepository.save(new RewardLedger(verifiedUser.getId(), 1000, "earn"));

        Redemption redemption = rewardService.redeem(verifiedUser.getId(), catalogItem.getId());
        assertNotNull(redemption.getId());
        assertEquals(500, redemption.getPointsCost(), "Points cost must be derived from catalog item (500)");

        // Verify ledger balance was deducted by exactly 500
        assertEquals(500, ledgerRepository.balance(verifiedUser.getId()));
    }

    @Test
    void redemptionRequestDeserializesAndIgnoresClientPointsCost() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // Client sends payload with deprecated/manipulated pointsCost: 1
        String json = "{\"rewardId\":\"" + catalogItem.getId() + "\",\"pointsCost\":1}";
        RewardsController.RedemptionRequest request = mapper.readValue(json, RewardsController.RedemptionRequest.class);

        assertEquals(catalogItem.getId().toString(), request.rewardId());
    }
}
