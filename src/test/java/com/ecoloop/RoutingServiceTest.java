package com.ecoloop;

import com.ecoloop.routing.RoutingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingServiceTest {
    @Test
    void scoreUsesWeightedInputs() {
        double score = new RoutingService(null, null, null, null, null).score(true, 1, 1, 1, 1);
        assertEquals(1.0, score, 0.0001);
    }
}
