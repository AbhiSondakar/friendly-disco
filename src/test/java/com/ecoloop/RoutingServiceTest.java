package com.ecoloop;

import com.ecoloop.partner.Partner;
import com.ecoloop.routing.RoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoutingServiceTest {

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService(null, null, null, null, null);
    }

    @Test
    void scoreUsesWeightedInputs() {
        double score = routingService.score(true, 1, 1, 1, 1);
        assertEquals(1.0, score, 0.0001);
    }

    @Test
    void scoreUsesRenormalizedWeightsWithoutHardcodedValues() {
        // capability (0.50), rating (0.25), load (0.25)
        assertEquals(1.0, routingService.score(true, 1.0, 1.0), 0.0001);
        assertEquals(0.50, routingService.score(false, 1.0, 1.0), 0.0001);
        assertEquals(0.75, routingService.score(true, 1.0, 0.0), 0.0001);
        assertEquals(0.50, routingService.score(true, 0.0, 0.0), 0.0001);
        assertEquals(0.0, routingService.score(false, 0.0, 0.0), 0.0001);
    }

    @Test
    void matchesServiceAreaAcceptsUnrestrictedPartnerOrPickup() {
        Partner p = new Partner(UUID.randomUUID(), "Org", "recyc", "LIC1");
        // serviceAreas is null
        assertTrue(routingService.matchesServiceArea(p, "123 Main St, Seattle"));
        assertTrue(routingService.matchesServiceArea(p, null));
    }

    @Test
    void matchesServiceAreaMatchesSpecificAreas() {
        Partner p = new Partner(UUID.randomUUID(), "Org", "recyc", "LIC1");
        p.setServiceAreas("Seattle, Bellevue; Redmond");

        assertTrue(routingService.matchesServiceArea(p, "123 Pine St, Seattle, WA"));
        assertTrue(routingService.matchesServiceArea(p, "456 110th Ave, Bellevue, WA"));
        assertTrue(routingService.matchesServiceArea(p, "789 Microsoft Way, REDMOND, WA"));
        assertFalse(routingService.matchesServiceArea(p, "1000 Broadway, Tacoma, WA"));
    }
}
