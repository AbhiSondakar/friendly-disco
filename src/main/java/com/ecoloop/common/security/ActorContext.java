package com.ecoloop.common.security;

import java.util.UUID;

public record ActorContext(UUID userId, Role role, UUID partnerId) {
    public ActorContext(UUID userId, Role role) {
        this(userId, role, null);
    }

    public boolean isPartner()   { return role == Role.PARTNER; }
    public boolean isHousehold() { return role == Role.HOUSEHOLD; }
    public boolean isAdmin()     { return role == Role.ADMIN; }

    public ActorContext withPartnerId(UUID partnerId) {
        return new ActorContext(userId, role, partnerId);
    }
}
