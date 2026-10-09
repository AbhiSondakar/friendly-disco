package com.ecoloop.rewards;

import com.ecoloop.common.web.PageResponse;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/rewards")
@Validated
@PreAuthorize("hasRole('HOUSEHOLD')")
public class RewardsController {
    private static final Logger log = LoggerFactory.getLogger(RewardsController.class);

    private final RewardLedgerRepository ledger;
    private final RewardCatalogRepository catalog;
    private final RedemptionRepository redemptions;
    private final RewardService rewardService;

    public RewardsController(RewardLedgerRepository l, RewardCatalogRepository c,
                             RedemptionRepository redemptions, RewardService rewardService) {
        this.ledger = l;
        this.catalog = c;
        this.redemptions = redemptions;
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
    public PageResponse<RewardLedgerDto> history(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            HttpServletRequest r) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RewardLedger> ledgerPage = ledger.findAllByUserId(user(r), pageable);
        return PageResponse.of(ledgerPage).mapContent(RewardLedgerDto::from);
    }

    @GetMapping("/redemptions")
    public PageResponse<RewardRedemptionDto> redemptions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            HttpServletRequest r) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Redemption> redemptionPage = redemptions.findAllByUserId(user(r), pageable);
        return PageResponse.of(redemptionPage).mapContent(RewardRedemptionDto::from);
    }

    @GetMapping("/catalog")
    public List<RewardCatalogItemDto> catalog() {
        return catalog.findAllByActiveTrue().stream()
            .map(RewardCatalogItemDto::from)
            .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Redemption request payload. pointsCost is deprecated and ignored as pricing is server-derived.")
    public record RedemptionRequest(
        @NotBlank @JsonAlias("catalogItemId") String rewardId
    ) {
        public RedemptionRequest(String rewardId, Integer pointsCost) {
            this(rewardId);
        }
    }

    @PostMapping("/redeem")
    public RedemptionResponse redeem(@Valid @RequestBody RedemptionRequest b,
                                     HttpServletRequest r) {
        var redemption = rewardService.redeem(user(r), UUID.fromString(b.rewardId()));
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
