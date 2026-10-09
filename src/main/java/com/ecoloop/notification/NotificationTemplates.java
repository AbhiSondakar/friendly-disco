package com.ecoloop.notification;

import java.util.Map;
import java.util.UUID;

public final class NotificationTemplates {

    private NotificationTemplates() {}

    public record Rendered(String title, String body) {}

    public static Rendered render(NotificationType type, Map<String, Object> params) {
        return switch (type) {
            case PICKUP_ACCEPTED -> new Rendered(
                "Pickup accepted",
                "A partner has accepted your pickup request and will be in touch shortly."
            );
            case PICKUP_REOFFERED -> new Rendered(
                "Pickup needs a new partner",
                "Your pickup was not accepted by the partner. We are finding another one."
            );
            case PICKUP_CANCELLED -> new Rendered(
                "Pickup cancelled",
                "The pickup you were assigned has been cancelled by the household."
            );
            case PICKUP_VERIFIED -> new Rendered(
                "Pickup verified",
                "Your pickup has been verified on site by the partner and is moving to completion."
            );
            case PICKUP_COMPLETED -> new Rendered(
                "Pickup complete",
                "Your pickup has been completed. Thank you for recycling responsibly."
            );
            case PICKUP_DELIVERED -> new Rendered(
                "E-waste delivered to warehouse",
                "Your e-waste has been delivered to the recycling warehouse. Points have been added to your account."
            );
            case PICKUP_REASSIGNED -> new Rendered(
                "Pickup reassigned",
                "Your pickup has been reassigned to a different partner."
            );
            case PARTNER_APPROVED -> new Rendered(
                "Partner account approved",
                "Great news — your partner application has been approved. You can now accept pickup offers."
            );
            case PARTNER_SUSPENDED -> new Rendered(
                "Partner account suspended",
                "Your partner account has been suspended. Please contact support for details on next steps."
            );
            case PARTNER_CHANGED -> new Rendered(
                "Partner status change",
                "One of the partners on your account has changed status. Any active jobs may be reassigned."
            );
        };
    }

    static String str(Object v, String fallback) {
        if (v == null) return fallback;
        String s = v.toString();
        return s.isBlank() ? fallback : s;
    }

    static String formatRef(UUID id) {
        if (id == null) return "—";
        String s = id.toString();
        return s.length() <= 8 ? s : s.substring(0, 8);
    }
}
