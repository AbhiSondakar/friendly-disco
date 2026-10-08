package com.ecoloop.partner;

import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.SessionUser;
import com.ecoloop.common.upload.FileStorageService;
import com.ecoloop.identity.IdentityService;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.pickup.PickupRepository;
import com.ecoloop.pickup.PickupRequest;
import com.ecoloop.pickup.PickupService;
import com.ecoloop.pickup.VerifyRequest;
import com.ecoloop.routing.RoutingOffer;
import com.ecoloop.routing.RoutingOfferDto;
import com.ecoloop.routing.RoutingOfferRepository;
import com.ecoloop.routing.RoutingService;
import com.ecoloop.rewards.RewardLedgerRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/partners")
public class PartnerController {
    private static final Logger log = LoggerFactory.getLogger(PartnerController.class);

    private final PartnerRepository partners;
    private final UserRepository users;
    private final RoutingOfferRepository offers;
    private final PickupRepository pickups;
    private final RewardLedgerRepository ledger;
    private final IdentityService identityService;
    private final RoutingService routingService;
    private final PickupService pickupService;
    private final FileStorageService fileStorageService;

    public PartnerController(PartnerRepository partners, UserRepository users,
                             RoutingOfferRepository offers, PickupRepository pickups,
                             RewardLedgerRepository ledger, IdentityService identityService,
                             RoutingService routingService, PickupService pickupService,
                             FileStorageService fileStorageService) {
        this.partners = partners;
        this.users = users;
        this.offers = offers;
        this.pickups = pickups;
        this.ledger = ledger;
        this.identityService = identityService;
        this.routingService = routingService;
        this.pickupService = pickupService;
        this.fileStorageService = fileStorageService;
    }

    public record Registration(
        @NotBlank String orgName,
        @NotBlank String type,
        String licenseNo,
        String serviceAreas,
        String capabilities) {}

    @PostMapping
    public PartnerDto register(@Valid @RequestBody Registration body, HttpServletRequest request) {
        var sessionUser = SessionUser.require(request);
        User user = users.findById(sessionUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Email must be verified before registering as a partner");
        }
        if (partners.findByUserId(sessionUser.id()).isPresent()) {
            throw new IllegalStateException("Partner profile already exists");
        }
        Partner partner = new Partner(sessionUser.id(), body.orgName(), body.type(), body.licenseNo());
        if (body.serviceAreas() != null) partner.setServiceAreas(body.serviceAreas());
        if (body.capabilities() != null) partner.setCapabilities(body.capabilities());
        partner.setCreatedAt(Instant.now());
        partner.setUpdatedAt(Instant.now());
        partner = partners.save(partner);
        log.info("Partner application submitted: user={}", sessionUser.id());
        return PartnerDto.from(partner, true);
    }

    @PostMapping(value = "/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadLicense(@RequestPart("license") MultipartFile license, HttpServletRequest request) throws IOException {
        var sessionUser = SessionUser.require(request);
        Partner partner = partners.findByUserId(sessionUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner profile not found"));

        FileStorageService.StoredFile stored = fileStorageService.storeFile(sessionUser.id(), "licenses", license, true);
        partner.setLicenseUploadId(stored.metadata().getId());
        partner.setUpdatedAt(Instant.now());
        partners.save(partner);

        log.info("Partner license uploaded: partnerId={}", partner.getId());
        return Map.of("uploadId", stored.metadata().getId(), "url", stored.publicUri());
    }

    @GetMapping("/offers")
    @PreAuthorize("hasRole('PARTNER')")
    public List<RoutingOfferDto> offers(HttpServletRequest request) {
        Partner partner = currentPartner(request);
        return offers.findAllByPartnerIdOrderByCreatedAtDesc(partner.getId()).stream()
            .map(RoutingOfferDto::from)
            .toList();
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasRole('PARTNER')")
    public List<com.ecoloop.pickup.PickupWithDevice> jobs(HttpServletRequest request) {
        Partner partner = currentPartner(request);
        return pickupService.enrich(pickups.findAllByPartnerId(partner.getId()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PARTNER')")
    public PartnerDto me(HttpServletRequest request) {
        return PartnerDto.from(currentPartner(request), true);
    }

    public record PartnerUpdate(
        String orgName,
        String serviceAreas,
        String capabilities,
        Integer capacity) {}

    @PatchMapping("/me")
    @PreAuthorize("hasRole('PARTNER')")
    public PartnerDto updateMe(@RequestBody PartnerUpdate body, HttpServletRequest request) {
        Partner partner = currentPartner(request);
        if (body.orgName() != null) partner.setOrgName(body.orgName());
        if (body.serviceAreas() != null) partner.setServiceAreas(body.serviceAreas());
        if (body.capabilities() != null) partner.setCapabilities(body.capabilities());
        if (body.capacity() != null) partner.setCapacity(body.capacity());
        partner.setUpdatedAt(Instant.now());
        return PartnerDto.from(partners.save(partner), true);
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable UUID id, HttpServletRequest request) {
        SessionUser user = SessionUser.require(request);
        Partner partner = partners.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Partner not found"));
        boolean canSeeSensitive = "ADMIN".equalsIgnoreCase(user.role()) || user.id().equals(partner.getUserId());
        if (canSeeSensitive) {
            return PartnerDto.from(partner, true);
        }
        return PartnerPublicDto.from(partner);
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasRole('PARTNER')")
    public Map<String, Object> kpis(HttpServletRequest request) {
        Partner partner = currentPartner(request);
        UUID partnerId = partner.getId();
        Instant today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant monthStart = java.time.LocalDate.now(java.time.ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        long offersToday = offers.findAllByPartnerIdOrderByCreatedAtDesc(partnerId).stream()
            .filter(o -> o.getCreatedAt().isAfter(today)).count();
        long activeJobs = pickups.countActiveJobsByPartnerId(partnerId);
        long monthlyCompletions = pickups.findAllByPartnerId(partnerId).stream()
            .filter(p -> "completed".equals(p.getStatus()))
            .filter(p -> p.getCompletedAt() != null && p.getCompletedAt().isAfter(monthStart))
            .filter(p -> users.findById(p.getUserId()).map(u -> !u.isDeleted()).orElse(false))
            .count();
        int pointsBalance = ledger.balance(partner.getUserId());
        Instant nextOfferAt = offers.findAllByPartnerIdOrderByCreatedAtDesc(partnerId).stream()
            .filter(o -> "offered".equals(o.getStatus()))
            .map(RoutingOffer::getExpiresAt)
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("offersToday", offersToday);
        result.put("activeJobs", activeJobs);
        result.put("monthlyCompletions", monthlyCompletions);
        result.put("pointsBalance", pointsBalance);
        result.put("nextOfferAt", nextOfferAt != null ? nextOfferAt.toString() : null);
        result.put("capacityUsed", activeJobs);
        result.put("capacityTotal", partner.getCapacity());
        return result;
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("hasRole('PARTNER')")
    public com.ecoloop.pickup.PickupWithDevice job(@PathVariable UUID id, HttpServletRequest request) {
        Partner partner = currentPartner(request);
        PickupRequest pickup = pickups.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Pickup not found"));
        if (!partner.getId().equals(pickup.getPartnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not assigned to this partner");
        }
        return pickupService.enrich(pickup);
    }

    @PostMapping("/offers/{id}/accept")
    @PreAuthorize("hasRole('PARTNER')")
    public RoutingOfferDto acceptOffer(@PathVariable UUID id, ActorContext actor) {
        return RoutingOfferDto.from(routingService.acceptOffer(actor.userId(), id));
    }

    @PostMapping("/offers/{id}/reject")
    @PreAuthorize("hasRole('PARTNER')")
    public RoutingOfferDto rejectOffer(@PathVariable UUID id,
                                       @RequestBody(required = false) Map<String, String> body,
                                       HttpServletRequest request) {
        String reason = body != null ? body.get("reason") : null;
        return RoutingOfferDto.from(routingService.rejectOffer(SessionUser.require(request).id(), id, reason));
    }

    @PostMapping("/jobs/{id}/verify")
    @PreAuthorize("hasRole('PARTNER')")
    public com.ecoloop.pickup.PickupWithDevice verifyJob(@PathVariable UUID id,
                                                         @RequestBody Map<String, Object> body,
                                                         ActorContext actor) {
        String category = body.get("category") != null ? body.get("category").toString() : null;
        String condition = body.get("condition") != null ? body.get("condition").toString() : null;
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;
        String evidenceUrl = body.get("evidenceUrl") != null ? body.get("evidenceUrl").toString() : null;

        PickupRequest pickup = pickupService.verifyAssignedPickup(actor, id,
            new VerifyRequest(category, condition, notes, evidenceUrl));
        return pickupService.enrich(pickup);
    }

    @PostMapping("/jobs/{id}/complete")
    @PreAuthorize("hasRole('PARTNER')")
    public com.ecoloop.pickup.PickupWithDevice completeJob(@PathVariable UUID id, ActorContext actor) {
        return pickupService.enrich(pickupService.completeAssignedPickup(actor, id));
    }

    private Partner currentPartner(HttpServletRequest request) {
        var sessionUser = SessionUser.require(request);
        User user = users.findById(sessionUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (user.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has been deactivated");
        }
        Partner partner = partners.findByUserId(sessionUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner profile not found"));
        if (!"approved".equalsIgnoreCase(partner.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Partner application is not approved");
        }
        return partner;
    }
}
