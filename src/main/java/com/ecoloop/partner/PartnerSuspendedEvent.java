package com.ecoloop.partner;

import java.util.List;
import java.util.UUID;

public record PartnerSuspendedEvent(
    UUID partnerId,
    UUID userId,
    List<UUID> affectedHouseholdUserIds
) {}
