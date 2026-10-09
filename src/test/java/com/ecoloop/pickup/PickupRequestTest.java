package com.ecoloop.pickup;

import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PickupRequestTest {

    @Test
    void assignTo_Success() {
        PickupRequest req = new PickupRequest(UUID.randomUUID(), UUID.randomUUID(), "123 Main St");
        UUID partnerId = UUID.randomUUID();
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);
        
        req.assignTo(actor, partnerId);
        
        assertEquals("assigned", req.getStatus());
        assertEquals(partnerId, req.getPartnerId());
        assertNotNull(req.getAssignedAt());
    }

    @Test
    void startTransitBy_Success() {
        PickupRequest req = new PickupRequest(UUID.randomUUID(), UUID.randomUUID(), "123 Main St");
        UUID partnerId = UUID.randomUUID();
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);
        
        req.assignTo(actor, partnerId);
        req.startTransitBy(actor);
        
        assertEquals("in_transit", req.getStatus());
        assertNotNull(req.getInTransitAt());
    }

    @Test
    void markCollectedBy_Success() {
        PickupRequest req = new PickupRequest(UUID.randomUUID(), UUID.randomUUID(), "123 Main St");
        UUID partnerId = UUID.randomUUID();
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);
        
        req.assignTo(actor, partnerId);
        req.startTransitBy(actor);
        req.markCollectedBy(actor);
        
        assertEquals("collected", req.getStatus());
        assertNotNull(req.getCollectedAt());
    }

    @Test
    void deliverBy_Success() {
        PickupRequest req = new PickupRequest(UUID.randomUUID(), UUID.randomUUID(), "123 Main St");
        UUID partnerId = UUID.randomUUID();
        ActorContext actor = new ActorContext(UUID.randomUUID(), Role.PARTNER, partnerId);
        
        req.assignTo(actor, partnerId);
        req.startTransitBy(actor);
        req.markCollectedBy(actor);
        req.deliverBy(actor, "WH-1", "WH-1");
        
        assertEquals("delivered", req.getStatus());
        assertNotNull(req.getDeliveredAt());
    }
}
