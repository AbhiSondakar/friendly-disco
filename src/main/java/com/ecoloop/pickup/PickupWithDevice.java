package com.ecoloop.pickup;

import com.ecoloop.device.Device;

import com.ecoloop.partner.Partner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PickupWithDevice(
    UUID id,
    UUID userId,
    UUID deviceId,
    UUID partnerId,
    String status,
    String address,
    Double pickupLat,
    Double pickupLon,
    Instant scheduledAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt,
    // Embedded verification details
    String verifiedCategory,
    String verifiedCondition,
    String verificationNotes,
    Instant verifiedAt,
    UUID verifiedBy,
    // Embedded device details for the frontend
    String deviceCategory,
    String deviceCondition,
    String deviceImageUrl,
    String deviceAiCategory,
    BigDecimal deviceAiConfidence,
    String deviceAiStatus,
    // Embedded partner details
    String partnerName
) {
    public static PickupWithDevice from(PickupRequest pickup, Device device, Partner partner) {
        String partnerName = partner != null ? partner.getOrgName() : null;
        if (device == null) {
            return new PickupWithDevice(
                pickup.getId(), pickup.getUserId(), pickup.getDeviceId(),
                pickup.getPartnerId(), pickup.getStatus(), pickup.getAddress(),
                pickup.getPickupLat(), pickup.getPickupLon(),
                pickup.getScheduledAt(), pickup.getCompletedAt(),
                pickup.getCreatedAt(), pickup.getUpdatedAt(),
                pickup.getVerifiedCategory(), pickup.getVerifiedCondition(), pickup.getVerificationNotes(), pickup.getVerifiedAt(), pickup.getVerifiedBy(),
                null, null, null, null, null, null,
                partnerName
            );
        }
        return new PickupWithDevice(
            pickup.getId(), pickup.getUserId(), pickup.getDeviceId(),
            pickup.getPartnerId(), pickup.getStatus(), pickup.getAddress(),
            pickup.getPickupLat(), pickup.getPickupLon(),
            pickup.getScheduledAt(), pickup.getCompletedAt(),
            pickup.getCreatedAt(), pickup.getUpdatedAt(),
            pickup.getVerifiedCategory(), pickup.getVerifiedCondition(), pickup.getVerificationNotes(), pickup.getVerifiedAt(), pickup.getVerifiedBy(),
            device.getCategory(), device.getCondition(), device.getImageUrl(),
            device.getAiCategory(), device.getAiConfidence(), device.getAiStatus(),
            partnerName
        );
    }
}
