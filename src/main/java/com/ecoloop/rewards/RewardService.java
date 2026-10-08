package com.ecoloop.rewards;

import com.ecoloop.identity.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RewardService {
    private final RewardLedgerRepository ledger;
    private final RewardCatalogRepository catalog;
    private final RedemptionRepository redemptions;
    private final UserRepository users;

    public RewardService(RewardLedgerRepository ledger, RewardCatalogRepository catalog,
                         RedemptionRepository redemptions, UserRepository users) {
        this.ledger = ledger;
        this.catalog = catalog;
        this.redemptions = redemptions;
        this.users = users;
    }

    @Transactional
    public Redemption redeem(UUID userId, UUID rewardId) {
        var user = users.findLockedById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Email must be verified before redeeming rewards");
        }
        var item = catalog.findById(rewardId)
            .orElseThrow(() -> new IllegalArgumentException("Reward not found"));
        if (!item.isActive()) {
            throw new IllegalArgumentException("Invalid reward");
        }
        int pointsCost = item.getPointsCost();
        int balance = ledger.balance(userId);
        if (balance < pointsCost) {
            throw new IllegalStateException("Insufficient points");
        }
        var redemption = new Redemption(userId, item.getId(), pointsCost);
        redemption.setCreatedAt(Instant.now());
        redemption.setStatus("completed");
        var saved = redemptions.save(redemption);
        ledger.save(new RewardLedger(userId, -pointsCost, "redeem",
            "Redeemed: " + item.getName(), saved.getId()));
        return saved;
    }

    @Deprecated
    @Transactional
    public Redemption redeem(UUID userId, UUID rewardId, int requestedCost) {
        return redeem(userId, rewardId);
    }
}
