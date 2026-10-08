package com.ecoloop.common.security;

import java.util.Locale;

public enum Role {
    HOUSEHOLD,
    PARTNER,
    ADMIN;

    public static Role fromString(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String clean = role.trim();
        if (clean.startsWith("ROLE_")) {
            clean = clean.substring(5);
        }
        try {
            return Role.valueOf(clean.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
