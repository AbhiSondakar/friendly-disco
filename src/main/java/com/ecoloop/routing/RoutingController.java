package com.ecoloop.routing;

import com.ecoloop.common.SessionUser;
import com.ecoloop.pickup.PickupService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
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

    @GetMapping("/offers")
    public List<RoutingOffer> offers(HttpServletRequest r) {
        return offers.findAllByPartnerIdOrderByCreatedAtDesc(partnerId(r));
    }

    @PostMapping("/offers/{id}/accept")
    public RoutingOffer accept(@PathVariable UUID id, HttpServletRequest r) {
        return routingService.acceptOffer(partnerUserId(r), id);
    }

    @PostMapping("/offers/{id}/reject")
    public RoutingOffer reject(@PathVariable UUID id, HttpServletRequest r) {
        return routingService.rejectOffer(partnerUserId(r), id);
    }

    @PostMapping("/offers/{id}/complete")
    public Object complete(@PathVariable UUID id, HttpServletRequest r) {
        UUID partnerUserId = partnerUserId(r);
        UUID partnerId = routingService.partnerIdForUser(partnerUserId);
        var offer = offers.findByIdAndPartnerId(id, partnerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        if (!"accepted".equals(offer.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Offer must be accepted before completion");
        }
        return pickupService.completeForPartnerUser(partnerUserId, offer.getPickupId());
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
