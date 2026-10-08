package com.ecoloop.pickup;

import java.util.UUID;

public record PickupReassignedEvent(
    UUID pickupId,
    UUID previousPartnerId,
    UUID newPartnerId,
    UUID notificationReferenceId
) {}
