package com.ecoloop.pickup;

import java.util.UUID;

public record PickupDeliveredEvent(UUID pickupId, UUID partnerId) {}
