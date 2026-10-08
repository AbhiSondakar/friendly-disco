package com.ecoloop.rewards;

import java.time.Instant;
import java.util.UUID;

public record RewardCatalogItemDto(
    UUID id,
    String name,
    String description,
    int pointsCost,
    String imageUrl,
    boolean active,
    Instant createdAt
) {
    public static RewardCatalogItemDto from(RewardCatalogItem item) {
        if (item == null) return null;
        return new RewardCatalogItemDto(
            item.getId(),
            item.getName(),
            item.getDescription(),
            item.getPointsCost(),
            item.getImageUrl(),
            item.isActive(),
            item.getCreatedAt()
        );
    }
}
