package com.ecoloop.rewards;

import com.ecoloop.identity.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/rewards")
public class RewardsController {
    private static final Logger log = LoggerFactory.getLogger(RewardsController.class);

    private final RewardLedgerRepository ledger;
    private final RewardCatalogRepository catalog;
    private final RewardService rewardService;

    public RewardsController(RewardLedgerRepository l, RewardCatalogRepository c,
                             RewardService rewardService) {
        this.ledger = l;
        this.catalog = c;
        this.rewardService = rewardService;
    }

    private UUID user(HttpServletRequest r) {
        return com.ecoloop.common.SessionUser.require(r).id();
    }

    @GetMapping("/balance")
    public Map<String, Object> balance(HttpServletRequest r) {
        UUID u = user(r);
        return Map.of("userId", u, "pointsBalance", ledger.balance(u));
    }

    @GetMapping("/ledger")
    public List<RewardLedger> history(HttpServletRequest r) {
        return ledger.findAllByUserId(user(r));
    }

    @GetMapping("/catalog")
    public List<RewardCatalogItem> catalog() {
        return catalog.findAllByActiveTrue();
    }

    public record RedemptionRequest(
        @jakarta.validation.constraints.NotBlank String rewardId,
        @Positive int pointsCost) {}

    @PostMapping("/redeem")
    public RedemptionResponse redeem(@Valid @RequestBody RedemptionRequest b,
                                     HttpServletRequest r) {
        var redemption = rewardService.redeem(user(r),
            UUID.fromString(b.rewardId()), b.pointsCost());
        log.info("Reward redeemed: user={} reward={} points={}",
                redemption.getUserId(), redemption.getCatalogItemId(), redemption.getPointsCost());
        return new RedemptionResponse("redeemed",
            redemption.getCatalogItemId(), redemption.getPointsCost());
    }

    public record RedemptionResponse(
        String status,
        UUID rewardId,
        int pointsSpent) {}
}
