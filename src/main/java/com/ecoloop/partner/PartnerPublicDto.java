package com.ecoloop.partner;

import java.math.BigDecimal;
import java.util.UUID;

public record PartnerPublicDto(
    UUID id,
    String orgName,
    String type,
    String serviceAreas,
    String capabilities,
    BigDecimal rating
) {
    public static PartnerPublicDto from(Partner p) {
        if (p == null) return null;
        return new PartnerPublicDto(
            p.getId(),
            p.getOrgName(),
            p.getType(),
            p.getServiceAreas(),
            p.getCapabilities(),
            p.getRating()
        );
    }
}
