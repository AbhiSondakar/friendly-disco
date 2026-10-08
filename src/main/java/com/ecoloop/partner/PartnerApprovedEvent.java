package com.ecoloop.partner;

import java.util.UUID;

public record PartnerApprovedEvent(
    UUID partnerId,
    UUID userId
) {}
