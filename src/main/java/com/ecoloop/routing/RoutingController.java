package com.ecoloop.routing;

import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.SessionUser;
import com.ecoloop.pickup.PickupService;
import com.ecoloop.pickup.PickupWithDevice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {
    private final RoutingOfferRepository offers;
    private final RoutingService routingService;
    private final PickupService pickupService;

    public RoutingController(RoutingOfferRepository offers, RoutingService routingService,
                             PickupService pickupService) {
        this.offers = offers;
        this.routingService = routingService;
        this.pickupService = pickupService;
    }

    @PreAuthorize("hasRole('PARTNER')")
    @GetMapping("/offers")
    public List<RoutingOfferDto> offers(HttpServletRequest r) {
        return offers.findAllByPartnerIdOrderByCreatedAtDesc(partnerId(r)).stream()
            .map(RoutingOfferDto::from)
            .toList();
    }

    @PreAuthorize("hasRole('PARTNER')")
    @PostMapping("/offers/{id}/accept")
    public RoutingOfferDto accept(@PathVariable UUID id, ActorContext actor) {
        return RoutingOfferDto.from(routingService.acceptOffer(actor.userId(), id));
    }

    @PreAuthorize("hasRole('PARTNER')")
    @PostMapping("/offers/{id}/reject")
    public RoutingOfferDto reject(@PathVariable UUID id, HttpServletRequest r) {
        return RoutingOfferDto.from(routingService.rejectOffer(partnerUserId(r), id));
    }

    @PreAuthorize("hasRole('PARTNER')")
    @PostMapping("/offers/{id}/complete")
    public PickupWithDevice complete(@PathVariable UUID id, ActorContext actor) {
        UUID partnerId = routingService.partnerIdForUser(actor.userId());
        var offer = offers.findByIdAndPartnerId(id, partnerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        if (!"accepted".equals(offer.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Offer must be accepted before completion");
        }
        return pickupService.enrich(pickupService.completeAssignedPickup(actor, offer.getPickupId()));
    }

    private UUID partnerUserId(HttpServletRequest r) {
        var sessionUser = SessionUser.require(r);
        sessionUser.requireRole("partner");
        return sessionUser.id();
    }

    private UUID partnerId(HttpServletRequest r) {
        return routingService.partnerIdForUser(partnerUserId(r));
    }
}
