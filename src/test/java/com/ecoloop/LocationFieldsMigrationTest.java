package com.ecoloop;

import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.pickup.PickupRepository;
import com.ecoloop.pickup.PickupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("postgres-test")
class LocationFieldsMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ecoloop_location_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    @Autowired UserRepository users;
    @Autowired PickupRepository pickups;
    @Autowired PartnerRepository partners;

    @Test
    void newLocationColumnsAcceptNullValues() {
        User pickupOwner = users.saveAndFlush(new User("pickup-" + java.util.UUID.randomUUID() + "@test.invalid", "hash", "Pickup", "HOUSEHOLD"));
        PickupRequest pickup = pickups.saveAndFlush(new PickupRequest(pickupOwner.getId(), null, "Somewhere"));
        assertNull(pickup.getPickupLat());
        assertNull(pickup.getPickupLon());

        User partnerOwner = users.saveAndFlush(new User("partner-" + java.util.UUID.randomUUID() + "@test.invalid", "hash", "Partner", "PARTNER"));
        Partner partner = partners.saveAndFlush(new Partner(partnerOwner.getId(), "Recycler", "recycler", null));
        assertNull(partner.getFacilityAddress());
        assertNull(partner.getFacilityLat());
        assertNull(partner.getFacilityLon());
    }
}
