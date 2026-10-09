package com.ecoloop.pickup;

public record OfferDto(
    String id,
    String pickupId,
    String partnerId,
    String status,
    String expiresAt,
    int round,
    String rejectionReason,
    String createdAt,
    String pickupAddress
) {
    public static OfferDto from(PickupRequest p) {
        return new OfferDto(
            p.getId() != null ? p.getId().toString() : null,
            p.getId() != null ? p.getId().toString() : null,
            p.getPartnerId() != null ? p.getPartnerId().toString() : null,
            "offered",
            null,
            0,
            null,
            p.getCreatedAt() != null ? p.getCreatedAt().toString() : null,
            p.getAddress()
        );
    }
}
