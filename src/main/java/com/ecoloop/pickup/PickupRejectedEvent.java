package com.ecoloop.pickup;

import java.util.UUID;

public record PickupRejectedEvent(
    UUID pickupId,
    UUID previousPartnerId
) {}
