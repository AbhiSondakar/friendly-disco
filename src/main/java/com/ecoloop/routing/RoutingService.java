package com.ecoloop.routing;

import com.ecoloop.audit.AuditService;
import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.security.Role;
import com.ecoloop.device.Device;
import com.ecoloop.device.DeviceRepository;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.notification.NotificationService;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.pickup.PickupAcceptedEvent;
import com.ecoloop.pickup.PickupCancelledEvent;
import com.ecoloop.pickup.PickupCreatedEvent;
import com.ecoloop.pickup.PickupRepository;
import com.ecoloop.pickup.PickupRequest;
import com.ecoloop.pickup.PickupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final RoutingOfferRepository offers;
    private final PartnerRepository partners;
    private final PickupRepository pickups;
    private final DeviceRepository devices;
    private final PickupService pickupService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final UserRepository users;

    public RoutingService(RoutingOfferRepository offers,
                          PartnerRepository partners,
                          PickupRepository pickups,
                          DeviceRepository devices,
                          PickupService pickupService) {
        this(offers, partners, pickups, devices, pickupService, null, null, null, null);
    }

    @Autowired
    public RoutingService(RoutingOfferRepository offers,
                          PartnerRepository partners,
                          PickupRepository pickups,
                          DeviceRepository devices,
                          PickupService pickupService,
                          ApplicationEventPublisher eventPublisher,
                          NotificationService notificationService,
                          AuditService auditService,
                          UserRepository users) {
        this.offers = offers;
        this.partners = partners;
        this.pickups = pickups;
        this.devices = devices;
        this.pickupService = pickupService;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.users = users;
    }

    public double score(boolean capabilityMatch, double ratingScore, double loadScore) {
        return 0.50 * (capabilityMatch ? 1.0 : 0.0)
            + 0.25 * ratingScore
            + 0.25 * loadScore;
    }

    @Deprecated
    public double score(boolean capabilityMatch, double conditionFit, double distanceScore,
                        double ratingScore, double loadScore) {
        return score(capabilityMatch, ratingScore, loadScore);
    }

    public boolean matchesServiceArea(Partner partner, String pickupAddress) {
        if (partner == null || partner.getServiceAreas() == null || partner.getServiceAreas().isBlank()) {
            return true;
        }
        if (pickupAddress == null || pickupAddress.isBlank()) {
            return true;
        }
        String normalizedAddress = pickupAddress.toLowerCase();
        String[] areas = partner.getServiceAreas().split("[,;\n\r]+");
        for (String area : areas) {
            String trimmed = area.trim().toLowerCase();
            if (!trimmed.isEmpty() && (trimmed.equals("*") || trimmed.equals("all") || normalizedAddress.contains(trimmed))) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public RoutingOffer acceptOffer(UUID partnerUserId, UUID offerId) {
        Partner partner = requirePartner(partnerUserId);
        RoutingOffer offer = offers.findById(offerId)
            .orElseThrow(() -> new AccessDeniedException("Offer not found"));

        pickupService.acceptOfferedPickup(new ActorContext(partnerUserId, Role.PARTNER), offer.getPickupId(), offerId);
        return offers.findById(offerId).orElse(offer);
    }

    @EventListener
    @Transactional
    public void onPickupCancelled(PickupCancelledEvent event) {
        cancelOffersForPickup(event.pickupId());
    }

    @EventListener
    @Transactional
    public void onPickupAccepted(PickupAcceptedEvent event) {
        for (RoutingOffer other : offers.findAllByPickupId(event.pickupId())) {
            if (!other.getPartnerId().equals(event.partnerId()) && "offered".equals(other.getStatus())) {
                other.setStatus("superseded");
                offers.save(other);
            }
        }
    }

    @EventListener
    @Transactional
    public void onPickupCreated(PickupCreatedEvent event) {
        boolean categoryNeedsReview = false;
        Optional<PickupRequest> pickupOpt = pickups.findById(event.pickupId());
        if (pickupOpt.isPresent()) {
            PickupRequest pickup = pickupOpt.get();
            if (pickup.getDeviceId() != null) {
                Optional<com.ecoloop.device.Device> dev = devices.findById(pickup.getDeviceId());
                if (dev.isPresent()) {
                    String cat = dev.get().getCategory();
                    String aiCat = dev.get().getAiCategory();
                    categoryNeedsReview = "other".equalsIgnoreCase(cat) || "other".equalsIgnoreCase(aiCat);
                }
            }
        }

        // Route to partners even when the AI flagged the category as 'other'/manual-review:
        // the assigned partner re-verifies the category at pickup time (verifyBy), so a
        // low-confidence classification must not strand the household without a partner.
        int dispatched = createTopNOffers(event.pickupId(), 1);
        if (dispatched == 0) {
            escalateToAdminQueue(event.pickupId(), "No eligible partners available for initial routing");
            return;
        }
        if (categoryNeedsReview) {
            flagCategoryForAdminReview(event.pickupId());
        }
    }

    public int createTopNOffers(UUID pickupId) {
        return createTopNOffers(pickupId, 1);
    }

    @Transactional
    public int createTopNOffers(UUID pickupId, int round) {
        Optional<PickupRequest> pickupOpt = pickups.findById(pickupId);
        if (pickupOpt.isEmpty()) return 0;
        PickupRequest pickup = pickupOpt.get();
        if (!"pending".equalsIgnoreCase(pickup.getStatus())) {
            log.info("Pickup {} status is {}, skipping offer generation", pickupId, pickup.getStatus());
            return 0;
        }

        String category = null;
        String aiCategory = null;
        if (pickup.getDeviceId() != null) {
            Optional<Device> devOpt = devices.findById(pickup.getDeviceId());
            if (devOpt.isPresent()) {
                category = devOpt.get().getCategory();
                aiCategory = devOpt.get().getAiCategory();
            }
        }

        boolean categoryNeedsReview = "other".equalsIgnoreCase(category) || "other".equalsIgnoreCase(aiCategory);

        List<Partner> allApproved = partners.findAllByStatus("approved");
        List<Partner> approvedPartners = allApproved; // Route to ANY approved partner, ignoring internal/external

        if (approvedPartners.isEmpty()) {
            log.info("No approved partners found for pickup {} (internalOnly={})", pickupId, categoryNeedsReview);
            return 0;
        }

        // Exclude partners who previously had ANY offer (offered, accepted, rejected, expired, cancelled, superseded)
        Set<UUID> previouslyOfferedPartnerIds = offers.findAllByPickupId(pickupId).stream()
            .map(RoutingOffer::getPartnerId)
            .collect(Collectors.toSet());

        Map<UUID, Long> activeJobsByPartner = new HashMap<>();
        List<UUID> candidatePartnerIds = approvedPartners.stream()
            .map(Partner::getId)
            .filter(id -> !previouslyOfferedPartnerIds.contains(id))
            .toList();

        if (!candidatePartnerIds.isEmpty()) {
            for (Object[] row : pickups.countActiveJobsByPartnerIds(candidatePartnerIds)) {
                activeJobsByPartner.put((UUID) row[0], (Long) row[1]);
            }
        }

        List<ScoredPartner> candidates = new ArrayList<>();
        for (Partner p : approvedPartners) {
            if (previouslyOfferedPartnerIds.contains(p.getId())) {
                continue; // Exclude prior offered / rejected / expired partners
            }

            if (!matchesServiceArea(p, pickup.getAddress())) {
                continue; // Exclude partners outside service area
            }

            long activeJobs = activeJobsByPartner.getOrDefault(p.getId(), 0L);
            if (activeJobs >= p.getCapacity()) {
                continue; // Partner is at full capacity
            }

            boolean capabilityMatch = p.getCapabilities() != null &&
                (category == null || p.getCapabilities().toLowerCase().contains(category.toLowerCase()));
            double loadScore = p.getCapacity() > 0 ? (1.0 - (double) activeJobs / p.getCapacity()) : 0.5;
            double ratingScore = p.getRating() != null ? Math.min(1.0, p.getRating().doubleValue() / 5.0) : 0.8;
            double totalScore = score(capabilityMatch, ratingScore, loadScore);

            candidates.add(new ScoredPartner(p, totalScore));
        }

        // Sort descending by score and pick top 5
        candidates.sort(Comparator.comparingDouble(ScoredPartner::score).reversed());
        List<ScoredPartner> topN = candidates.stream().limit(5).toList();

        int createdCount = 0;
        for (ScoredPartner sp : topN) {
            RoutingOffer offer = new RoutingOffer(
                pickupId,
                sp.partner().getId(),
                Instant.now().plusSeconds(24 * 3600),
                round
            );
            offer.setScore(sp.score());
            offers.save(offer);
            createdCount++;
        }
        log.info("Dispatched {} routing offers for pickup: {} (round {})", createdCount, pickupId, round);
        return createdCount;
    }

    @EventListener
    @Transactional
    public void onAllOffersExpired(AllOffersExpiredEvent event) {
        log.info("Handling AllOffersExpiredEvent for pickup: {}, round: {}", event.pickupId(), event.round());
        Optional<PickupRequest> pickupOpt = pickups.findById(event.pickupId());
        if (pickupOpt.isEmpty()) return;
        PickupRequest pickup = pickupOpt.get();
        if (!"pending".equalsIgnoreCase(pickup.getStatus())) {
            log.info("Pickup {} status is {}, skipping re-routing on expired offers", event.pickupId(), pickup.getStatus());
            return;
        }

        if (event.round() >= 3) {
            log.warn("Pickup {} reached maximum routing rounds (3), escalating to admin queue", event.pickupId());
            escalateToAdminQueue(event.pickupId(), "Maximum routing rounds (3) reached without partner acceptance");
            return;
        }

        int nextRound = event.round() + 1;
        int dispatched = createTopNOffers(event.pickupId(), nextRound);
        if (dispatched == 0) {
            log.warn("Pickup {} round {} produced no eligible offers, escalating to admin queue", event.pickupId(), nextRound);
            escalateToAdminQueue(event.pickupId(), "No eligible partners available for routing round " + nextRound);
        }
    }

    @Transactional
    public void checkAndHandleExpiredOffers(UUID pickupId) {
        Optional<PickupRequest> pickupOpt = pickups.findById(pickupId);
        if (pickupOpt.isEmpty()) return;
        PickupRequest pickup = pickupOpt.get();
        if (!"pending".equalsIgnoreCase(pickup.getStatus())) {
            return;
        }

        long activeOffers = offers.countActiveOffersByPickupId(pickupId, Instant.now());
        if (activeOffers == 0) {
            int maxRound = offers.findMaxRoundByPickupId(pickupId);
            if (maxRound == 0) {
                maxRound = 1;
            }
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new AllOffersExpiredEvent(pickupId, maxRound));
            }
        }
    }

    @Transactional
    public void escalateToAdminQueue(UUID pickupId, String reason) {
        log.warn("Escalating pickup {} to admin queue: {}", pickupId, reason);
        Optional<PickupRequest> pickupOpt = pickups.findById(pickupId);
        if (pickupOpt.isEmpty()) return;
        PickupRequest pickup = pickupOpt.get();

        if (users != null && notificationService != null) {
            List<User> admins = users.findAllByRole("ADMIN");
            for (User admin : admins) {
                notificationService.create(
                    admin.getId(),
                    "pickup_routing_escalated",
                    "Pickup Routing Escalated",
                    "Pickup " + pickupId + " escalated to admin queue: " + reason
                );
            }
        }

        if (notificationService != null && pickup.getUserId() != null) {
            notificationService.create(
                pickup.getUserId(),
                "pickup_routing_delayed",
                "Pickup Routing Update",
                "We are searching for an available partner for your pickup request. Platform administrators have been notified."
            );
        }

        if (auditService != null) {
            auditService.record(
                null,
                "SYSTEM",
                "pickup.routing_escalated",
                "pickup_requests",
                pickupId,
                "ESCALATED",
                Map.of("reason", reason)
            );
        }
    }

    /**
     * Notifies admins that a pickup was auto-classified as 'other' (manual review) while
     * routing to partners continues normally. Unlike {@link #escalateToAdminQueue}, the
     * household is NOT told routing is delayed — it is not. Reuses the existing
     * 'pickup_routing_escalated' notification type to satisfy ck_notifications_type.
     */
    @Transactional
    public void flagCategoryForAdminReview(UUID pickupId) {
        log.info("Flagging pickup {} for admin category review (routing to partners continues)", pickupId);
        if (users != null && notificationService != null) {
            List<User> admins = users.findAllByRole("ADMIN");
            for (User admin : admins) {
                notificationService.create(
                    admin.getId(),
                    "pickup_routing_escalated",
                    "Pickup Category Needs Review",
                    "Pickup " + pickupId + " was auto-classified as 'other' and flagged for review, " +
                        "but it has been routed to partners. Please verify the device category."
                );
            }
        }

        if (auditService != null) {
            auditService.record(
                null,
                "SYSTEM",
                "pickup.category_review",
                "pickup_requests",
                pickupId,
                "FLAGGED",
                Map.of("reason", "Category 'other' auto-flagged for admin review; routing proceeded")
            );
        }
    }

    public void cancelOffersForPickup(UUID pickupId) {
        for (RoutingOffer offer : offers.findAllByPickupId(pickupId)) {
            if ("offered".equals(offer.getStatus()) || "accepted".equals(offer.getStatus())) {
                offer.setStatus("cancelled");
                offers.save(offer);
            }
        }
    }

    @Transactional
    public RoutingOffer rejectOffer(UUID partnerUserId, UUID offerId) {
        return rejectOffer(partnerUserId, offerId, null);
    }

    @Transactional
    public RoutingOffer rejectOffer(UUID partnerUserId, UUID offerId, String reason) {
        Partner partner = requirePartner(partnerUserId);
        RoutingOffer offer = offers.findByIdAndPartnerIdForUpdate(offerId, partner.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));

        enforceOfferNotExpired(offer);

        if (!Set.of("offered", "accepted").contains(offer.getStatus())) {
            throw new IllegalStateException("Offer is no longer available (current status: " + offer.getStatus() + ")");
        }

        String previousStatus = offer.getStatus();
        offer.reject(reason);
        RoutingOffer saved = offers.save(offer);
        log.info("Offer {} rejected by partner {}: reason={}", offerId, partner.getId(), reason);
        
        if ("accepted".equals(previousStatus)) {
            pickupService.rejectAssignedPickup(new ActorContext(partnerUserId, Role.PARTNER), saved.getPickupId(), reason);
        }
        
        return saved;
    }

    private Partner requirePartner(UUID partnerUserId) {
        return partners.findByUserId(partnerUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Partner profile not found"));
    }

    public UUID partnerIdForUser(UUID partnerUserId) {
        return requirePartner(partnerUserId).getId();
    }

    public void enforceOfferNotExpired(RoutingOffer offer) {
        if (offer.getExpiresAt() == null) {
            return;
        }
        Instant now = Instant.now();
        if (offer.getExpiresAt().isBefore(now) && "offered".equalsIgnoreCase(offer.getStatus())) {
            log.warn("Offer {} has expired (expiresAt={}, now={}); transitioning status to expired",
                offer.getId(), offer.getExpiresAt(), now);
            offer.setStatus("expired");
            offers.save(offer);
            checkAndHandleExpiredOffers(offer.getPickupId());
        }
        if (offer.getExpiresAt().isBefore(now)) {
            log.warn("Expired offer accessed: offerId={} status={} expiresAt={}",
                offer.getId(), offer.getStatus(), offer.getExpiresAt());
            throw new IllegalStateException("Offer has expired (expired at: " + offer.getExpiresAt() + ")");
        }
    }

    private record ScoredPartner(Partner partner, double score) {}
}
