package com.ecoloop.notification;

import com.ecoloop.common.security.ActorContext;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.pickup.PickupCompletedEvent;
import com.ecoloop.pickup.PickupRepository;
import com.ecoloop.pickup.PickupRequest;
import com.ecoloop.routing.RoutingOffer;
import com.ecoloop.routing.RoutingOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("postgres-test")
class NotificationListenerFailureTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ecoloop_test")
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

    @TestConfiguration
    static class ThrowingListenerConfig {
        @Bean
        @Primary
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ThrowingAfterCommitListener throwingAfterCommitListener() {
            return new ThrowingAfterCommitListener();
        }
    }

    static class ThrowingAfterCommitListener {
        volatile int throwsCount = 0;

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onPickupCompleted(PickupCompletedEvent event) {
            throwsCount++;
            throw new RuntimeException("simulated listener failure in AFTER_COMMIT for test");
        }
    }

    @Autowired ThrowingAfterCommitListener throwingListener;
    @Autowired UserRepository users;
    @Autowired PartnerRepository partners;
    @Autowired PickupRepository pickups;
    @Autowired RoutingOfferRepository offers;

    private User household;
    private User partnerUser;
    private Partner partner;
    private PickupRequest pickup;

    @BeforeEach
    void setUp() {
        throwingListener.throwsCount = 0;
        offers.deleteAll();
        pickups.deleteAll();
        partners.deleteAll();
        users.deleteAllInBatch();

        household = users.saveAndFlush(new User("hh-fail-" + UUID.randomUUID() + "@ecoloop.test", "hash", "HH", "HOUSEHOLD"));
        partnerUser = users.saveAndFlush(new User("pa-fail-" + UUID.randomUUID() + "@ecoloop.test", "hash", "PA", "PARTNER"));
        partner = new Partner(partnerUser.getId(), "X Org", null, null);
        partner.setStatus("approved");
        partner = partners.saveAndFlush(partner);

        pickup = new PickupRequest(household.getId(), null, "Addr");
        pickup.setPartnerId(partner.getId());
        pickup.setStatus("verified");
        pickup = pickups.saveAndFlush(pickup);
    }

    @Test
    @Transactional
    void listenerRuntimeExceptionDoesNotRollbackPickupTransition() {
        ActorContext actor = new ActorContext(partnerUser.getId(), com.ecoloop.common.security.Role.PARTNER, partner.getId());
        // completeBy publishes PickupCompletedEvent via aggregate root.
        pickup.completeBy(actor);
        pickups.save(pickup);

        // Trigger the AFTER_COMMIT via manual transaction commit. Spring will invoke @TransactionalEventListener(AFTER_COMMIT),
        // which throws. We need to catch the propagated RuntimeException outside the committed TX.
        assertThrows(RuntimeException.class, () -> {
            // Force commit so AFTER_COMMIT listeners fire.
            // flush + manual commit via Spring infrastructure; use transactionTemplate isn't needed because
            // @Transactional test + end of method would normally roll back. Use explicit tx commit via org.springframework.test.context.transaction.TestTransaction.
            org.springframework.test.context.transaction.TestTransaction.flagForCommit();
            org.springframework.test.context.transaction.TestTransaction.end();
        });

        // Throwing listener must have been invoked (verifies the failure path was actually exercised).
        assertTrue(throwingListener.throwsCount >= 1, "Expected throwing listener to fire at least once");

        // The pickup transition (completed) must be persisted because the transaction committed BEFORE the listener ran.
        // Clear persistence context to force a fresh DB read.
        pickups.flush();
        PickupRequest reloaded = pickups.findById(pickup.getId()).orElseThrow();
        assertEquals("completed", reloaded.getStatus(),
            "Listener failure must not rollback pickup state transition — status=completed must remain committed");
        assertNotNull(reloaded.getCompletedAt());
    }
}
