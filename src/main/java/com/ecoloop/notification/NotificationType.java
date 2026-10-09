package com.ecoloop.notification;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Map;

public enum NotificationType {

    PICKUP_ACCEPTED("pickup_accepted"),
    PICKUP_REOFFERED("pickup_reoffered"),
    PICKUP_CANCELLED("pickup_cancelled"),
    PICKUP_VERIFIED("pickup_verified"),
    PICKUP_COMPLETED("pickup_completed"),
    PICKUP_DELIVERED("pickup_delivered"),
    PICKUP_REASSIGNED("pickup_reassigned"),

    PARTNER_APPROVED("partner_approved"),
    PARTNER_SUSPENDED("partner_suspended"),
    PARTNER_CHANGED("partner_changed");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    private static final Map<String, NotificationType> BY_VALUE =
        java.util.Arrays.stream(values())
            .collect(java.util.stream.Collectors.toUnmodifiableMap(NotificationType::value, t -> t));

    public static NotificationType fromValue(String s) {
        if (s == null) return null;
        NotificationType t = BY_VALUE.get(s.toLowerCase(Locale.ROOT));
        if (t == null) {
            t = BY_VALUE.get(s);
        }
        return t;
    }
}
