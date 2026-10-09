package com.ecoloop.partner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PartnerDto(
    UUID id,
    UUID userId,
    String orgName,
    String type,
    String licenseNo,
    UUID licenseUploadId,
    String serviceAreas,
    String capabilities,
    String status,
    BigDecimal rating,
    int capacity,
    Instant createdAt,
    Instant updatedAt,
    String facilityAddress,
    Double facilityLat,
    Double facilityLon,
    String warehouseId) {

    public static PartnerDto from(Partner p, boolean includeSensitive) {
        return new PartnerDto(
            p.getId(),
            p.getUserId(),
            p.getOrgName(),
            p.getType(),
            includeSensitive ? p.getLicenseNo() : null,
            includeSensitive ? p.getLicenseUploadId() : null,
            p.getServiceAreas(),
            p.getCapabilities(),
            p.getStatus(),
            p.getRating(),
            p.getCapacity(),
            p.getCreatedAt(),
            p.getUpdatedAt(),
            p.getFacilityAddress(),
            p.getFacilityLat(),
            p.getFacilityLon(),
            p.getWarehouseId()
        );
    }
}
