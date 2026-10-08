package com.ecoloop.routing;

import java.time.Instant;
import java.util.UUID;

public record RoutingOfferDto(
    UUID id,
    UUID pickupId,
    String status,
    Instant expiresAt,
    int round,
    String rejectionReason,
    Instant createdAt
) {
    public static RoutingOfferDto from(RoutingOffer o) {
        if (o == null) return null;
        return new RoutingOfferDto(
            o.getId(),
            o.getPickupId(),
            o.getStatus(),
            o.getExpiresAt(),
            o.getRound(),
            o.getRejectionReason(),
            o.getCreatedAt()
        );
    }
}
