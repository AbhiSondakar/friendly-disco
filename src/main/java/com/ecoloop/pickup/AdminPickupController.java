package com.ecoloop.pickup;

import com.ecoloop.common.web.PageResponse;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.common.security.ActorContext;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pickups")
@PreAuthorize("hasRole('ADMIN')")
@org.springframework.validation.annotation.Validated
public class AdminPickupController {
  private final PickupRepository pickups;
  private final PartnerRepository partners;
  private final PickupService pickupService;

  public AdminPickupController(PickupRepository pickups, PartnerRepository partners, PickupService pickupService) {
    this.pickups = pickups;
    this.partners = partners;
    this.pickupService = pickupService;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public PageResponse<PickupWithDevice> list(
      @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
      @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(200) int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String search) {
    PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Specification<PickupRequest> spec = Specification.where(PickupSpecifications.withStatus(status))
            .and(PickupSpecifications.withSearch(search));
    Page<PickupRequest> pickupPage = pickups.findAll(spec, pageable);
    List<PickupWithDevice> enriched = pickupService.enrich(pickupPage.getContent());
    return new PageResponse<>(enriched, pickupPage.getTotalElements(), pickupPage.getTotalPages(),
            pickupPage.getNumber(), pickupPage.getSize());
  }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/escalated")
  public List<PickupWithDevice> escalated() {
    // Pickups that auto-routing could not place: pending, unassigned, no live offers.
    // Admins assign a partner manually from this queue via the reassign endpoint.
    return pickupService.enrich(pickups.findUnassigned());
  }

  public record ReassignRequest(
      @NotNull(message = "newPartnerId is required")
      @JsonAlias("partnerId")
      UUID newPartnerId
  ) {}

  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{id}/reassign")
  public PickupWithDevice reassign(
      ActorContext actor,
      @PathVariable UUID id,
      @Valid @RequestBody ReassignRequest body) {
    PickupRequest reassigned = pickupService.reassignPickup(actor, id, body.newPartnerId());
    return pickupService.enrich(reassigned);
  }
}
