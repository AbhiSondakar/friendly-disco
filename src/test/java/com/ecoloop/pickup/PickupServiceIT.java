package com.ecoloop.pickup;

import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.security.Role;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PickupServiceIT {

    @Autowired PickupService pickupService;
    @Autowired PickupRepository pickups;
    @Autowired UserRepository users;
    @Autowired PartnerRepository partners;

    @Test
    void claimPickup_Success() {
        User u = users.save(new User("h1@example.com", "hash", "H"));
        User pU = users.save(new User("p1@example.com", "hash", "P"));
        Partner p = partners.save(new Partner(pU.getId(), "Org", "type", "lic"));
        p.setStatus("approved");
        partners.save(p);
        
        PickupRequest req = pickups.save(new PickupRequest(u.getId(), UUID.randomUUID(), "123"));
        
        ActorContext actor = new ActorContext(pU.getId(), Role.PARTNER, p.getId());
        PickupRequest result = pickupService.claimPickup(actor, req.getId());
        
        assertEquals("assigned", result.getStatus());
        assertEquals(p.getId(), result.getPartnerId());
    }

    @Test
    void startTransit_Success() {
        User u = users.save(new User("h2@example.com", "hash", "H"));
        User pU = users.save(new User("p2@example.com", "hash", "P"));
        Partner p = partners.save(new Partner(pU.getId(), "Org", "type", "lic"));
        p.setStatus("approved");
        partners.save(p);
        
        PickupRequest req = pickups.save(new PickupRequest(u.getId(), UUID.randomUUID(), "123"));
        ActorContext actor = new ActorContext(pU.getId(), Role.PARTNER, p.getId());
        pickupService.claimPickup(actor, req.getId());
        
        PickupRequest result = pickupService.startTransit(actor, req.getId());
        assertEquals("in_transit", result.getStatus());
    }

    @Test
    void markCollected_Success() {
        User u = users.save(new User("h3@example.com", "hash", "H"));
        User pU = users.save(new User("p3@example.com", "hash", "P"));
        Partner p = partners.save(new Partner(pU.getId(), "Org", "type", "lic"));
        p.setStatus("approved");
        partners.save(p);
        
        PickupRequest req = pickups.save(new PickupRequest(u.getId(), UUID.randomUUID(), "123"));
        ActorContext actor = new ActorContext(pU.getId(), Role.PARTNER, p.getId());
        pickupService.claimPickup(actor, req.getId());
        pickupService.startTransit(actor, req.getId());
        
        PickupRequest result = pickupService.markCollected(actor, req.getId());
        assertEquals("collected", result.getStatus());
    }

    @Test
    void deliverPickup_Success() {
        User u = users.save(new User("h4@example.com", "hash", "H"));
        User pU = users.save(new User("p4@example.com", "hash", "P"));
        Partner p = partners.save(new Partner(pU.getId(), "Org", "type", "lic"));
        p.setStatus("approved");
        p.setWarehouseId("WH-1");
        partners.save(p);
        
        PickupRequest req = pickups.save(new PickupRequest(u.getId(), UUID.randomUUID(), "123"));
        ActorContext actor = new ActorContext(pU.getId(), Role.PARTNER, p.getId());
        pickupService.claimPickup(actor, req.getId());
        pickupService.startTransit(actor, req.getId());
        pickupService.markCollected(actor, req.getId());
        
        PickupRequest result = pickupService.deliverPickup(actor, req.getId(), "WH-1");
        assertEquals("delivered", result.getStatus());
    }

    @Test
    void claimPickup_CapacityExceeded() {
        User u = users.save(new User("h5@example.com", "hash", "H"));
        User pU = users.save(new User("p5@example.com", "hash", "P"));
        Partner p = partners.save(new Partner(pU.getId(), "Org", "type", "lic"));
        p.setStatus("approved");
        p.setCapacity(0); // Exceed capacity immediately
        partners.save(p);
        
        PickupRequest req = pickups.save(new PickupRequest(u.getId(), UUID.randomUUID(), "123"));
        ActorContext actor = new ActorContext(pU.getId(), Role.PARTNER, p.getId());
        
        assertThrows(IllegalStateException.class, () -> pickupService.claimPickup(actor, req.getId()));
    }

    @Test
    void deliverPickup_WrongWarehouseId() {
        User u = users.save(new User("h6@example.com", "hash", "H"));
        User pU = users.save(new User("p6@example.com", "hash", "P"));
        Partner p = partners.save(new Partner(pU.getId(), "Org", "type", "lic"));
        p.setStatus("approved");
        p.setWarehouseId("WH-1");
        partners.save(p);
        
        PickupRequest req = pickups.save(new PickupRequest(u.getId(), UUID.randomUUID(), "123"));
        ActorContext actor = new ActorContext(pU.getId(), Role.PARTNER, p.getId());
        pickupService.claimPickup(actor, req.getId());
        pickupService.startTransit(actor, req.getId());
        pickupService.markCollected(actor, req.getId());
        
        assertThrows(IllegalStateException.class, () -> pickupService.deliverPickup(actor, req.getId(), "WRONG"));
    }
}
