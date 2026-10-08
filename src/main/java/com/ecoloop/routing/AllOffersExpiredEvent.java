package com.ecoloop.routing;

import java.util.UUID;

public record AllOffersExpiredEvent(UUID pickupId, int round) {}
