package com.ecoloop.pickup;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/pickups")
public class PickupController {
    private static final Logger log = LoggerFactory.getLogger(PickupController.class);

    private final PickupRepository pickups;
    private final PickupService pickupService;

    public PickupController(PickupRepository p, PickupService pickupService) {
        this.pickups = p;
        this.pickupService = pickupService;
    }

    private UUID user(HttpServletRequest r) {
        return com.ecoloop.common.SessionUser.require(r).id();
    }

    public record CreatePickup(
        UUID deviceId,
        @NotBlank String address,
        Instant scheduledAt) {}

    @PostMapping(value = "/submit-household", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public PickupWithDevice submitHousehold(
        @RequestPart("image") org.springframework.web.multipart.MultipartFile image,
        @RequestParam(defaultValue = "good") String condition,
        @RequestParam @NotBlank String address,
        @RequestParam(required = false) Instant scheduledAt,
        HttpServletRequest r) throws java.io.IOException {
        UUID userId = user(r);
        return pickupService.submitHouseholdPickup(userId, image, condition, address, scheduledAt);
    }

    @PostMapping
    public PickupWithDevice create(@Valid @RequestBody CreatePickup body, HttpServletRequest r) {
        PickupRequest pickup = pickupService.createPickup(user(r), body.deviceId(), body.address(), body.scheduledAt());
        log.info("Pickup created: id={} user={} device={}", pickup.getId(), user(r), body.deviceId());
        return pickupService.enrich(pickup);
    }

    @GetMapping
    public List<PickupWithDevice> list(HttpServletRequest r) {
        return pickupService.enrich(pickups.findAllByUserIdOrderByCreatedAtDesc(user(r)));
    }

    @GetMapping("/{id}")
    public PickupWithDevice get(@PathVariable UUID id, HttpServletRequest r) {
        PickupRequest pickup = pickups.findByIdAndUserId(id, user(r))
            .orElseThrow(() -> new NoSuchElementException("Pickup not found"));
        return pickupService.enrich(pickup);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('PARTNER')")
    public PickupWithDevice accept(@PathVariable UUID id, HttpServletRequest r) {
        UUID actor = user(r);
        PickupRequest result = pickupService.acceptByPartnerUser(actor, id);
        log.info("Pickup accepted: id={} by partner={}", id, actor);
        return pickupService.enrich(result);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('PARTNER')")
    public PickupWithDevice complete(@PathVariable UUID id, HttpServletRequest r) {
        UUID actor = user(r);
        PickupRequest result = pickupService.completeForPartnerUser(actor, id);
        log.info("Pickup completed: id={} by partner={}", id, actor);
        return pickupService.enrich(result);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('PARTNER')")
    public PickupWithDevice reject(@PathVariable UUID id, HttpServletRequest r) {
        UUID actor = user(r);
        PickupRequest result = pickupService.rejectByPartnerUser(actor, id);
        log.info("Pickup rejected: id={} by partner={}", id, actor);
        return pickupService.enrich(result);
    }

    @PostMapping("/{id}/cancel")
    public PickupWithDevice cancel(@PathVariable UUID id, HttpServletRequest r) {
        PickupRequest saved = pickupService.cancelPickup(user(r), id);
        log.info("Pickup cancelled: id={} user={}", id, user(r));
        return pickupService.enrich(saved);
    }
}
