package com.ecoloop.notification;

import com.ecoloop.identity.NotificationPreference;
import com.ecoloop.identity.NotificationPreferenceRepository;
import com.ecoloop.partner.PartnerApprovedEvent;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.partner.PartnerSuspendedEvent;
import com.ecoloop.pickup.*;
import com.ecoloop.routing.RoutingOfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationService notifications;
    private final RoutingOfferRepository offers;
    private final PickupRepository pickups;
    private final PartnerRepository partners;
    private final NotificationPreferenceRepository preferences;
    private final NotificationDeliveryHandler deliveryHandler;

    public NotificationListener(NotificationService notifications,
                                RoutingOfferRepository offers,
                                PickupRepository pickups,
                                PartnerRepository partners,
                                NotificationPreferenceRepository preferences,
                                NotificationDeliveryHandler deliveryHandler) {
        this.notifications = notifications;
        this.offers = offers;
        this.pickups = pickups;
        this.partners = partners;
        this.preferences = preferences;
        this.deliveryHandler = deliveryHandler;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPickupCreated(PickupCreatedEvent event) {
        UUID pickupId = event.pickupId();
        List<UUID> partnerUserIds = offers.findOfferedPartnerUserIdsByPickupId(pickupId);
        var gate = gate(NotificationType.OFFER_RECEIVED);
        for (UUID userId : partnerUserIds) {
            Notification n = notifications.create(userId, NotificationType.OFFER_RECEIVED, pickupId, Map.of("pickupId", pickupId));
            deliveryHandler.offerIfEnabled(userId, NotificationType.OFFER_RECEIVED, n, gate);
        }
        log.debug("PickupCreated event fan-out: pickupId={} offeredPartners={}", pickupId, partnerUserIds.size());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPickupAccepted(PickupAcceptedEvent event) {
        UUID pickupId = event.pickupId();
        UUID acceptedPartnerId = event.partnerId();
        pickups.findById(pickupId).ifPresent(pickup -> {
            var householdGate = gate(NotificationType.PICKUP_ACCEPTED);
            Notification hh = notifications.create(pickup.getUserId(), NotificationType.PICKUP_ACCEPTED, pickupId, Map.of("pickupId", pickupId, "partnerId", acceptedPartnerId));
            deliveryHandler.offerIfEnabled(pickup.getUserId(), NotificationType.PICKUP_ACCEPTED, hh, householdGate);

            List<UUID> supersededUserIds = offers.findSupersededPartnerUserIdsByPickupId(pickupId, acceptedPartnerId);
            var supersededGate = gate(NotificationType.OFFER_SUPERSEDED);
            for (UUID partnerUserId : supersededUserIds) {
                Notification n = notifications.create(partnerUserId, NotificationType.OFFER_SUPERSEDED, pickupId, Map.of("pickupId", pickupId, "acceptedPartnerId", acceptedPartnerId));
                deliveryHandler.offerIfEnabled(partnerUserId, NotificationType.OFFER_SUPERSEDED, n, supersededGate);
            }
            log.debug("PickupAccepted event fan-out: pickupId={} household={} superseded={}", pickupId, pickup.getUserId(), supersededUserIds.size());
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPickupRejected(PickupRejectedEvent event) {
        UUID pickupId = event.pickupId();
        pickups.findById(pickupId).ifPresent(pickup -> {
            Notification n = notifications.create(pickup.getUserId(), NotificationType.PICKUP_REOFFERED, pickupId,
                Map.of("pickupId", pickupId, "previousPartnerId", event.previousPartnerId()));
            deliveryHandler.offerIfEnabled(pickup.getUserId(), NotificationType.PICKUP_REOFFERED, n, gate(NotificationType.PICKUP_REOFFERED));
            log.debug("PickupRejected event fan-out: pickupId={} household={}", pickupId, pickup.getUserId());
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPickupCancelled(PickupCancelledEvent event) {
        UUID pickupId = event.pickupId();
        pickups.findById(pickupId).ifPresent(pickup -> {
            // Assigned partner (accepted jobs) OR partners whose open offers RoutingService just cancelled.
            Set<UUID> recipients = new LinkedHashSet<>();
            UUID partnerId = pickup.getPartnerId();
            if (partnerId != null) {
                partners.findUserIdByPartnerId(partnerId).ifPresent(recipients::add);
            }
            recipients.addAll(offers.findCancelledOfferPartnerUserIdsByPickupId(pickupId));
            if (recipients.isEmpty()) {
                log.debug("PickupCancelled event with no partner recipients. pickupId={}", pickupId);
                return;
            }
            for (UUID partnerUserId : recipients) {
                Notification n = notifications.create(partnerUserId, NotificationType.PICKUP_CANCELLED, pickupId, Map.of("pickupId", pickupId));
                // Always delivered — bypass preference gate per spec.
                deliveryHandler.offerAlways(partnerUserId, NotificationType.PICKUP_CANCELLED, n);
            }
            log.debug("PickupCancelled event fan-out: pickupId={} partners={}", pickupId, recipients.size());
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPickupReassigned(PickupReassignedEvent event) {
        pickups.findById(event.pickupId()).ifPresent(pickup -> {
            Map<String, Object> parameters = Map.of(
                "pickupId", event.pickupId(),
                "newPartnerId", event.newPartnerId());
            Notification household = notifications.create(
                pickup.getUserId(), NotificationType.PICKUP_REASSIGNED,
                event.notificationReferenceId(), parameters);
            deliveryHandler.offerIfEnabled(
                pickup.getUserId(), NotificationType.PICKUP_REASSIGNED, household,
                gate(NotificationType.PICKUP_REASSIGNED));

            if (event.previousPartnerId() != null) {
                partners.findUserIdByPartnerId(event.previousPartnerId()).ifPresent(previousPartnerUserId -> {
                    Notification previousPartner = notifications.create(
                        previousPartnerUserId, NotificationType.PICKUP_REASSIGNED,
                        event.notificationReferenceId(), parameters);
                    deliveryHandler.offerIfEnabled(
                        previousPartnerUserId, NotificationType.PICKUP_REASSIGNED, previousPartner,
                        gate(NotificationType.PICKUP_REASSIGNED));
                });
            }
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPickupVerified(PickupVerifiedEvent event) {
        UUID pickupId = event.pickupId();
        pickups.findById(pickupId).ifPresent(pickup -> {
            Notification n = notifications.create(pickup.getUserId(), NotificationType.PICKUP_VERIFIED, pickupId,
                Map.of("pickupId", pickupId, "partnerId", event.partnerId()));
            deliveryHandler.offerIfEnabled(pickup.getUserId(), NotificationType.PICKUP_VERIFIED, n, gate(NotificationType.PICKUP_VERIFIED));
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPickupCompleted(PickupCompletedEvent event) {
        UUID pickupId = event.pickupId();
        pickups.findById(pickupId).ifPresent(pickup -> {
            Notification n = notifications.create(pickup.getUserId(), NotificationType.PICKUP_COMPLETED, pickupId,
                Map.of("pickupId", pickupId, "partnerId", event.partnerId()));
            deliveryHandler.offerIfEnabled(pickup.getUserId(), NotificationType.PICKUP_COMPLETED, n, gate(NotificationType.PICKUP_COMPLETED));
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPartnerApproved(PartnerApprovedEvent event) {
        UUID userId = event.userId();
        Notification n = notifications.create(userId, NotificationType.PARTNER_APPROVED, event.partnerId(),
            Map.of("partnerId", event.partnerId()));
        deliveryHandler.offerAlways(userId, NotificationType.PARTNER_APPROVED, n);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPartnerSuspended(PartnerSuspendedEvent event) {
        UUID userId = event.userId();
        Notification toPartner = notifications.create(userId, NotificationType.PARTNER_SUSPENDED, event.partnerId(),
            Map.of("partnerId", event.partnerId()));
        deliveryHandler.offerAlways(userId, NotificationType.PARTNER_SUSPENDED, toPartner);

        var householdGate = gate(NotificationType.PARTNER_CHANGED);
        List<UUID> affected = event.affectedHouseholdUserIds();
        if (affected == null) return;
        for (UUID householdUserId : affected) {
            Notification hh = notifications.create(householdUserId, NotificationType.PARTNER_CHANGED, event.partnerId(),
                Map.of("partnerId", event.partnerId()));
            deliveryHandler.offerIfEnabled(householdUserId, NotificationType.PARTNER_CHANGED, hh, householdGate);
        }
    }

    /**
     * Reads notification preference row ONCE per event type (shared across recipients in this listener call).
     * The returned GatePredicate defaults to enabled=true when no preference row exists (default enabled per spec).
     */
    private GatePredicate gate(NotificationType type) {
        NotificationPreference defaults = new NotificationPreference(null);
        Map<NotificationType, java.util.function.Function<NotificationPreference, Boolean>> mapping = new HashMap<>();
        mapping.put(NotificationType.OFFER_RECEIVED, NotificationPreference::getOfferAlerts);
        mapping.put(NotificationType.OFFER_SUPERSEDED, NotificationPreference::getOfferAlerts);
        mapping.put(NotificationType.PICKUP_ACCEPTED, NotificationPreference::getPickupUpdates);
        mapping.put(NotificationType.PICKUP_REOFFERED, NotificationPreference::getPickupUpdates);
        mapping.put(NotificationType.PICKUP_VERIFIED, NotificationPreference::getPickupUpdates);
        mapping.put(NotificationType.PICKUP_COMPLETED, NotificationPreference::getPickupUpdates);
        mapping.put(NotificationType.PICKUP_REASSIGNED, NotificationPreference::getPickupUpdates);
        mapping.put(NotificationType.PARTNER_CHANGED, NotificationPreference::getPickupUpdates);
        java.util.function.Function<NotificationPreference, Boolean> accessor = mapping.get(type);
        if (accessor == null) {
            return (p) -> true;
        }
        return (pref) -> pref == null ? true : accessor.apply(pref);
    }

    @FunctionalInterface
    interface GatePredicate {
        boolean isDeliveryEnabledFor(NotificationPreference pref);
    }
}
