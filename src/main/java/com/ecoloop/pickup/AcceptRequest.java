package com.ecoloop.pickup;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AcceptRequest(@NotNull UUID offerId) {}
