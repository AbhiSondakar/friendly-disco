package com.ecoloop.device;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeviceDto(
    UUID id,
    UUID userId,
    String category,
    String condition,
    String imageUrl,
    String aiCategory,
    BigDecimal aiConfidence,
    String aiStatus,
    String aiProvider,
    Instant createdAt,
    Instant updatedAt
) {
    public static DeviceDto from(Device d) {
        if (d == null) return null;
        return new DeviceDto(
            d.getId(),
            d.getUserId(),
            d.getCategory(),
            d.getCondition(),
            d.getImageUrl(),
            d.getAiCategory(),
            d.getAiConfidence(),
            d.getAiStatus(),
            d.getAiProvider(),
            d.getCreatedAt(),
            d.getUpdatedAt()
        );
    }
}
