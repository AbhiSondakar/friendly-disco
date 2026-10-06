package com.ecoloop.pickup;

import com.ecoloop.common.web.PageResponse;
import com.ecoloop.partner.PartnerRepository;
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
public class AdminPickupController {
  private final PickupRepository pickups;
  private final PartnerRepository partners;
  private final PickupService pickupService;

  public AdminPickupController(PickupRepository pickups, PartnerRepository partners, PickupService pickupService) {
    this.pickups = pickups;
    this.partners = partners;
    this.pickupService = pickupService;
  }

  @GetMapping
  public PageResponse<PickupWithDevice> list(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String search) {
    int boundedPage = Math.max(0, page);
    int boundedSize = Math.min(Math.max(1, size), 100);
    PageRequest pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    Specification<PickupRequest> spec = Specification.where(PickupSpecifications.withStatus(status))
            .and(PickupSpecifications.withSearch(search));
    Page<PickupRequest> pickupPage = pickups.findAll(spec, pageable);
    List<PickupWithDevice> enriched = pickupService.enrich(pickupPage.getContent());
    return new PageResponse<>(enriched, pickupPage.getTotalElements(), pickupPage.getTotalPages(),
            pickupPage.getNumber(), pickupPage.getSize());
  }

  public record ReassignRequest(UUID partnerId) {}

  @PostMapping("/{id}/reassign")
  public PickupWithDevice reassign(@PathVariable UUID id,
                                @RequestBody(required = false) ReassignRequest body) {
    PickupRequest pickup = pickups.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pickup not found"));
    if (body != null && body.partnerId() != null) {
      partners.findById(body.partnerId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner not found"));
      pickup.setPartnerId(body.partnerId());
    } else {
      pickup.setPartnerId(null);
    }
    pickup.setUpdatedAt(Instant.now());
    return pickupService.enrich(pickups.save(pickup));
  }
}
