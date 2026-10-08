package com.ecoloop.pickup;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public record CreatePickup(
    UUID deviceId,
    @NotBlank String address,
    Instant scheduledAt) {}
