package com.ecoloop.notification;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Map;

public enum NotificationType {
    OFFER_RECEIVED("offer_received"),
    OFFER_SUPERSEDED("offer_superseded"),
    PICKUP_ACCEPTED("pickup_accepted"),
    PICKUP_REOFFERED("pickup_reoffered"),
    PICKUP_CANCELLED("pickup_cancelled"),
    PICKUP_VERIFIED("pickup_verified"),
    PICKUP_COMPLETED("pickup_completed"),
    PICKUP_REASSIGNED("pickup_reassigned"),
    PICKUP_ROUTING_ESCALATED("pickup_routing_escalated"),
    PICKUP_ROUTING_DELAYED("pickup_routing_delayed"),
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
