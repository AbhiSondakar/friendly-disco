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
    public Redemption redeem(UUID userId, UUID rewardId, int requestedCost) {
        users.findLockedById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        var item = catalog.findById(rewardId)
            .orElseThrow(() -> new IllegalArgumentException("Reward not found"));
        if (!item.isActive() || item.getPointsCost() != requestedCost) {
            throw new IllegalArgumentException("Invalid reward");
        }
        int balance = ledger.balance(userId);
        if (balance < item.getPointsCost()) {
            throw new IllegalStateException("Insufficient points");
        }
        var redemption = new Redemption(userId, item.getId(), item.getPointsCost());
        redemption.setCreatedAt(Instant.now());
        redemption.setStatus("completed");
        var saved = redemptions.save(redemption);
        ledger.save(new RewardLedger(userId, -item.getPointsCost(), "redeem",
            "Redeemed: " + item.getName(), saved.getId()));
        return saved;
    }
}
