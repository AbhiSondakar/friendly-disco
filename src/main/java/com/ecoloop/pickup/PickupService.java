package com.ecoloop.pickup;

import com.ecoloop.audit.AuditService;
import com.ecoloop.classification.api.ClassificationApi;
import com.ecoloop.classification.api.ClassificationResult;
import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.upload.FileStorageService;
import com.ecoloop.device.Device;
import com.ecoloop.device.DeviceRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.rewards.RewardLedger;
import com.ecoloop.rewards.RewardLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class PickupService {

    private static final Logger log = LoggerFactory.getLogger(PickupService.class);

    private final PickupRepository pickups;
    private final PartnerRepository partners;
    private final RewardLedgerRepository ledger;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final DeviceRepository devices;
    private final FileStorageService fileStorageService;
    private final ClassificationApi classificationApi;
        private final TransactionTemplate transactionTemplate;

    public PickupService(PickupRepository pickups,
                         PartnerRepository partners,
                         RewardLedgerRepository ledger,
                         AuditService audit,
                         ApplicationEventPublisher events,
                         DeviceRepository devices,
                         FileStorageService fileStorageService,
                         ClassificationApi classificationApi,
                         PlatformTransactionManager transactionManager) {
        this.pickups = pickups;
        this.partners = partners;
        this.ledger = ledger;
        this.audit = audit;
        this.events = events;
        this.devices = devices;
        this.fileStorageService = fileStorageService;
        this.classificationApi = classificationApi;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public PickupRequest createPickup(ActorContext actor, CreatePickup request) {
        if (actor == null || (!actor.isHousehold() && !actor.isAdmin())) {
            throw new AccessDeniedException("Only households can create pickups");
        }
        if (request == null || request.deviceId() == null) {
            throw new IllegalArgumentException("Device ID is required");
        }

        Device device = devices.findById(request.deviceId())
            .orElseThrow(() -> new NoSuchElementException("Device not found"));

        if (!actor.isAdmin() && !actor.userId().equals(device.getUserId())) {
            throw new AccessDeniedException("Device does not belong to this user");
        }

        if (pickups.findActiveByDeviceId(request.deviceId()).isPresent()) {
            throw new IllegalStateException("Device already has an active pickup request");
        }

        PickupRequest pickup = new PickupRequest(actor.userId(), request.deviceId(),
            request.address() != null ? request.address().trim() : "");
        if (request.scheduledAt() != null) {
            pickup.setScheduledAt(request.scheduledAt());
        }
        pickup.setCreatedAt(Instant.now());
        pickup.setUpdatedAt(Instant.now());
        pickup = pickups.save(pickup);
        events.publishEvent(new PickupCreatedEvent(pickup.getId()));
        return pickup;
    }

    public PickupWithDevice submitHouseholdPickup(ActorContext actor,
                                                  MultipartFile image,
                                                  String condition,
                                                  String address,
                                                  Instant scheduledAt,
                                                  Double pickupLat,
                                                  Double pickupLon) throws IOException {
        if (actor == null || (!actor.isHousehold() && !actor.isAdmin())) {
            throw new AccessDeniedException("Only households can submit pickups");
        }

        UUID userId = actor.userId();
        FileStorageService.StoredFile stored = fileStorageService.storeFile(userId, "devices", image, false);
        String mime = stored.metadata().getContentType();
        String imageUrl = stored.publicUri();
        UUID deviceId = UUID.randomUUID();

        ClassificationResult result = classificationApi.classify(stored.content(), mime, deviceId, imageUrl);

        if ("failed".equals(result.status())) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, 
                "AI classification failed due to a backend issue. Please try again later."
            );
        }

        if (result.confidence() == 0.0 && !result.requiresManualReview()) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY, 
                "AI model could not classify the image. Please provide a clearer picture."
            );
        }

        return transactionTemplate.execute(status -> {
            Device device = new Device();
            device.setId(deviceId);
            device.setUserId(userId);
            device.setCondition(condition != null ? condition.trim() : "good");
            device.setImageUrl(imageUrl);
            device.setCreatedAt(Instant.now());
            device.setUpdatedAt(Instant.now());
            device.setCategory(result.category());
            device.setAiCategory(result.category());
            device.setAiConfidence(BigDecimal.valueOf(result.confidence()));
            device.setAiProvider(result.provider());
            device.setAiStatus(result.deviceAiStatus());
            device = devices.save(device);

            PickupRequest pickup = new PickupRequest(userId, device.getId(), address != null ? address.trim() : "");
            pickup.setPickupLat(pickupLat);
            pickup.setPickupLon(pickupLon);
            if (scheduledAt != null) {
                pickup.setScheduledAt(scheduledAt);
            }
            pickup.setCreatedAt(Instant.now());
            pickup.setUpdatedAt(Instant.now());
            pickup = pickups.save(pickup);

            events.publishEvent(new PickupCreatedEvent(pickup.getId()));
            log.info("Atomic household pickup created: pickupId={} deviceId={} category={}",
                    pickup.getId(), device.getId(), result.category());

            return PickupWithDevice.from(pickup, device, null);
        });
    }

    @Transactional
    public PickupRequest cancelOwnedPickup(ActorContext actor, UUID pickupId) {
        PickupRequest p = pickups.findById(pickupId)
            .orElseThrow(() -> new NoSuchElementException("Pickup not found"));
        p.cancelBy(actor);
        return pickups.save(p);
    }

    @Transactional
    public PickupRequest cancelOwnedPickupByDevice(ActorContext actor, UUID deviceId) {
        if (actor == null || (!actor.isHousehold() && !actor.isAdmin())) {
            throw new AccessDeniedException("Only households can cancel their pickups");
        }
        PickupRequest pickup = pickups.findActiveByUserIdAndDeviceId(actor.userId(), deviceId)
            .orElseThrow(() -> new NoSuchElementException("No active pickup found for this device"));
        pickup.cancelBy(actor);
        return pickups.save(pickup);
    }

    @Transactional
    public PickupRequest verifyAssignedPickup(ActorContext actor, UUID pickupId, VerifyRequest request) {
        if (actor == null || !actor.isPartner()) {
            throw new AccessDeniedException("Only assigned partner can verify pickup");
        }
        Partner partner = requirePartner(actor.userId());
        PickupRequest pickup = pickups.findByIdForUpdate(pickupId)
            .orElseThrow(() -> new NoSuchElementException("Pickup not found"));

        pickup.verifyBy(actor.withPartnerId(partner.getId()), request);
        return pickups.save(pickup);
    }

    @Transactional
    public PickupRequest completeAssignedPickup(ActorContext actor, UUID pickupId) {
        if (actor == null || !actor.isPartner()) {
            throw new AccessDeniedException("Only assigned partner can complete pickup");
        }
        Partner partner = requirePartner(actor.userId());
        PickupRequest pickup = pickups.findByIdForUpdate(pickupId)
            .orElseThrow(() -> new NoSuchElementException("Pickup not found"));

        boolean alreadyCompleted = "completed".equals(pickup.getStatus());
        pickup.completeBy(actor.withPartnerId(partner.getId()));
        PickupRequest saved = pickups.save(pickup);

        if (!alreadyCompleted) {
            UUID householdId = pickup.getUserId();
            try {
                if (ledger.findByUserIdAndReferenceId(householdId, pickup.getId()).isEmpty()) {
                    ledger.save(new RewardLedger(householdId, 25, "earn",
                        "Pickup completed reward", pickup.getId()));
                }
            } catch (DataIntegrityViolationException ex) {
                log.info("Reward already awarded for pickup: {}", pickup.getId());
            }
            // Completion notification is owned by NotificationListener on PickupCompletedEvent
            // so households do not receive a duplicate row from this service path.

            audit.record(householdId, "partner", "pickup.completed",
                "pickup", pickup.getId(), "success");
        }

        return saved;
    }

    @Transactional
    public PickupRequest reassignPickup(ActorContext actor, UUID pickupId, UUID newPartnerId) {
        if (actor == null || !actor.isAdmin()) {
            throw new AccessDeniedException("Only admins can reassign pickups");
        }
        if (newPartnerId == null) {
            throw new IllegalArgumentException("newPartnerId is required");
        }

        PickupRequest pickup = pickups.findByIdForUpdate(pickupId)
            .orElseThrow(() -> new NoSuchElementException("Pickup not found"));

        UUID previousPartnerId = pickup.getPartnerId();
        // A client retry after a committed reassignment is a no-op.  In particular,
        // it must not create another event or notification.
        if (newPartnerId.equals(previousPartnerId)) {
            return pickup;
        }

        Partner targetPartner = partners.findByIdForUpdate(newPartnerId)
            .orElseThrow(() -> new NoSuchElementException("Target partner not found"));

        if (!"approved".equalsIgnoreCase(targetPartner.getStatus())) {
            throw new IllegalStateException("Target partner is not approved");
        }

        long activeJobs = pickups.countActiveJobsByPartnerId(targetPartner.getId());
        if (activeJobs >= targetPartner.getCapacity()) {
            throw new IllegalStateException("Target partner has reached maximum active capacity");
        }

        pickup.reassignBy(actor, targetPartner.getId());
        PickupRequest saved = pickups.save(pickup);

        audit.record(actor.userId(), "ADMIN", "pickup.reassigned",
            "pickup", saved.getId(), "success");

        return saved;
    }

    @Transactional
    public PickupRequest claimPickup(ActorContext actor, UUID pickupId) {
        if (actor == null || !actor.isPartner()) throw new AccessDeniedException("Only partners can claim pickups");
        Partner partner = requirePartner(actor.userId());

        long activeJobs = pickups.countActiveJobsByPartnerId(partner.getId());
        if (activeJobs >= partner.getCapacity())
            throw new IllegalStateException("You have reached your maximum active capacity");

        PickupRequest pickup = pickups.findByIdForUpdate(pickupId)
            .orElseThrow(() -> new NoSuchElementException("Pickup not found"));
        pickup.assignTo(actor.withPartnerId(partner.getId()), partner.getId());
        return pickups.save(pickup);
    }

    @Transactional
    public PickupRequest startTransit(ActorContext actor, UUID pickupId) {
        if (actor == null || !actor.isPartner()) throw new AccessDeniedException("Only partners can start transit");
        Partner partner = requirePartner(actor.userId());
        PickupRequest pickup = pickups.findByIdForUpdate(pickupId)
            .orElseThrow(() -> new NoSuchElementException("Pickup not found"));
        pickup.startTransitBy(actor.withPartnerId(partner.getId()));
        return pickups.save(pickup);
    }

    @Transactional
    public PickupRequest markCollected(ActorContext actor, UUID pickupId) {
        if (actor == null || !actor.isPartner()) throw new AccessDeniedException("Only partners can mark collected");
        Partner partner = requirePartner(actor.userId());
        PickupRequest pickup = pickups.findByIdForUpdate(pickupId)
            .orElseThrow(() -> new NoSuchElementException("Pickup not found"));
        pickup.markCollectedBy(actor.withPartnerId(partner.getId()));
        return pickups.save(pickup);
    }

    @Transactional
    public PickupRequest deliverPickup(ActorContext actor, UUID pickupId, String warehouseId) {
        if (actor == null || !actor.isPartner()) throw new AccessDeniedException("Only partners can deliver pickups");
        Partner partner = requirePartner(actor.userId());
        PickupRequest pickup = pickups.findByIdForUpdate(pickupId)
            .orElseThrow(() -> new NoSuchElementException("Pickup not found"));
        pickup.deliverBy(actor.withPartnerId(partner.getId()), warehouseId, partner.getWarehouseId());

        UUID householdId = pickup.getUserId();
        try {
            if (ledger.findByUserIdAndReferenceId(householdId, pickup.getId()).isEmpty()) {
                ledger.save(new RewardLedger(householdId, 25, "earn",
                    "Pickup delivered to warehouse", pickup.getId()));
            }
        } catch (DataIntegrityViolationException ex) {
            log.info("Reward already awarded for pickup: {}", pickup.getId());
        }
        
        events.publishEvent(new PickupDeliveredEvent(pickup.getId(), partner.getId()));
        
        audit.record(householdId, "partner", "pickup.delivered",
            "pickup", pickup.getId(), "success");
        return pickups.save(pickup);
    }

    private Partner requirePartner(UUID partnerUserId) {
        Partner partner = partners.findByUserId(partnerUserId)
            .orElseThrow(() -> new AccessDeniedException("Partner profile not found"));
        if (!"approved".equalsIgnoreCase(partner.getStatus())) {
            throw new AccessDeniedException("Partner is not approved");
        }
        return partner;
    }

    public PickupWithDevice enrich(PickupRequest pickup) {
        Device device = null;
        if (pickup.getDeviceId() != null) {
            device = devices.findById(pickup.getDeviceId()).orElse(null);
        }
        Partner partner = null;
        if (pickup.getPartnerId() != null) {
            partner = partners.findById(pickup.getPartnerId()).orElse(null);
        }
        return PickupWithDevice.from(pickup, device, partner);
    }

    public List<PickupWithDevice> enrich(List<PickupRequest> pickupList) {
        return pickupList.stream().map(this::enrich).toList();
    }
}


