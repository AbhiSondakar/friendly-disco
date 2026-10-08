package com.ecoloop.rewards;

import java.time.Instant;
import java.util.UUID;

public record RewardRedemptionDto(
    UUID id,
    UUID userId,
    UUID catalogItemId,
    int pointsCost,
    String status,
    Instant createdAt
) {
    public static RewardRedemptionDto from(Redemption r) {
        if (r == null) return null;
        return new RewardRedemptionDto(
            r.getId(),
            r.getUserId(),
            r.getCatalogItemId(),
            r.getPointsCost(),
            r.getStatus(),
            r.getCreatedAt()
        );
    }
}
