package com.ecoloop.pickup;

import java.util.UUID;

public record PickupCompletedEvent(
    UUID pickupId,
    UUID partnerId
) {}
