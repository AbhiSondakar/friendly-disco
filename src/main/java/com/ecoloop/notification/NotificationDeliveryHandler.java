package com.ecoloop.notification;

import com.ecoloop.identity.NotificationPreference;
import com.ecoloop.identity.NotificationPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * External delivery hook.
 *
 * For Phase 3A Step 8, Option A (in-app inbox only — no external push/email today).
 * This class encapsulates the preference-gating decision at SEND TIME (not CREATE time),
 * which is the correct seam to plug in Email / FCM later without touching listener code.
 *
 * Behavior today:
 * - Row creation is always handled by NotificationListener calling NotificationService.create.
 * - External delivery is a no-op; preferences are loaded and the decision is traced at DEBUG.
 */
@Component
public class NotificationDeliveryHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryHandler.class);

    private final NotificationPreferenceRepository preferences;
    public NotificationDeliveryHandler(NotificationPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    public void offerIfEnabled(UUID userId,
                               NotificationType type,
                               Notification notification,
                               NotificationListener.GatePredicate gate) {
        NotificationPreference pref = loadPreference(userId);
        boolean send = gate.isDeliveryEnabledFor(pref);
        trace(userId, type, send, "preference-gated");
        // Option A: even if enabled, do not contact any external channel.
        // External channel (email/fcm) plug-in point: if (send) { externalAdapter.send(userId, notification); }
    }

    public void offerAlways(UUID userId, NotificationType type, Notification notification) {
        trace(userId, type, true, "always-delivered-type");
        // Option A: even "always delivered" types have no external push target today.
    }

    private NotificationPreference loadPreference(UUID userId) {
        return userId == null ? null : preferences.findByUserId(userId).orElse(null);
    }

    private void trace(UUID userId, NotificationType type, boolean send, String mode) {
        if (log.isDebugEnabled()) {
            log.debug("Notification external-delivery decision: userId={} type={} send={} mode={} channel=none(Option-A)",
                userId, type, send, mode);
        }
    }
}
