package com.ecoloop.rewards;

import java.time.Instant;
import java.util.UUID;

public record RewardLedgerDto(
    UUID id,
    UUID userId,
    int points,
    String type,
    String description,
    UUID referenceId,
    Instant createdAt
) {
    public static RewardLedgerDto from(RewardLedger l) {
        if (l == null) return null;
        return new RewardLedgerDto(
            l.getId(),
            l.getUserId(),
            l.getPoints(),
            l.getType(),
            l.getDescription(),
            l.getReferenceId(),
            l.getCreatedAt()
        );
    }
}
