package com.ecoloop.pickup;

import java.util.UUID;

public record PickupVerifiedEvent(
    UUID pickupId,
    UUID partnerId
) {}
