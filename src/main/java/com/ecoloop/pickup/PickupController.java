package com.ecoloop.pickup;

import com.ecoloop.common.security.ActorContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/pickups")
@PreAuthorize("hasRole('HOUSEHOLD')")
public class PickupController {
    private static final Logger log = LoggerFactory.getLogger(PickupController.class);

    private final PickupRepository pickups;
    private final PickupService pickupService;

    public PickupController(PickupRepository p, PickupService pickupService) {
        this.pickups = p;
        this.pickupService = pickupService;
    }

    @PostMapping(value = "/submit-household", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PickupWithDevice submitHousehold(
        @RequestPart("image") MultipartFile image,
        @RequestParam(defaultValue = "good") String condition,
        @RequestParam @NotBlank String address,
        @RequestParam(required = false) Instant scheduledAt,
        @RequestParam(required = false) Double pickupLat,
        @RequestParam(required = false) Double pickupLon,
        ActorContext actor) throws IOException {
        return pickupService.submitHouseholdPickup(actor, image, condition, address, scheduledAt, pickupLat, pickupLon);
    }

    @PostMapping
    public PickupWithDevice create(@Valid @RequestBody CreatePickup body, ActorContext actor) {
        PickupRequest pickup = pickupService.createPickup(actor, body);
        log.info("Pickup created: id={} user={} device={}", pickup.getId(), actor.userId(), body.deviceId());
        return pickupService.enrich(pickup);
    }

    @GetMapping
    public List<PickupWithDevice> list(ActorContext actor) {
        return pickupService.enrich(pickups.findAllByUserIdOrderByCreatedAtDesc(actor.userId()));
    }

    @GetMapping("/{id}")
    public PickupWithDevice get(@PathVariable UUID id, ActorContext actor) {
        PickupRequest pickup = pickups.findByIdAndUserId(id, actor.userId())
            .orElseThrow(() -> new NoSuchElementException("Pickup not found"));
        return pickupService.enrich(pickup);
    }

    @PostMapping("/{id}/cancel")
    public PickupWithDevice cancel(@PathVariable UUID id, ActorContext actor) {
        PickupRequest saved = pickupService.cancelOwnedPickup(actor, id);
        log.info("Pickup cancelled: id={} user={}", id, actor.userId());
        return pickupService.enrich(saved);
    }
}
