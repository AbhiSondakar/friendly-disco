package com.ecoloop;

import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.pickup.PickupRequest;
import com.ecoloop.pickup.PickupRepository;
import com.ecoloop.rewards.RewardLedger;
import com.ecoloop.rewards.RewardLedgerRepository;
import com.ecoloop.routing.RoutingOffer;
import com.ecoloop.routing.RoutingOfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("postgres-test")
public class PostgreSqlIntegrationTest {

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PickupRepository pickupRepository;

    @Autowired
    private RoutingOfferRepository routingOfferRepository;

    @Autowired
    private RewardLedgerRepository rewardLedgerRepository;

    @Test
    void postgresBootstrapAndFlywayValidated() {
        assertNotNull(userRepository);
    }

    @Test
    void duplicateNormalizedEmailRejected() {
        User u1 = new User("Test.User@EcoLoop.app", "hash1", "User 1", "HOUSEHOLD");
        userRepository.saveAndFlush(u1);

        User u2 = new User("test.user@ecoloop.app", "hash2", "User 2", "HOUSEHOLD");
        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(u2);
        });
    }

    @Test
    void oneRewardPerUserAndReferenceIdEnforced() {
        UUID userId = UUID.randomUUID();
        UUID refId = UUID.randomUUID();

        RewardLedger r1 = new RewardLedger(userId, 25, "earn", "Reward 1", refId);
        rewardLedgerRepository.saveAndFlush(r1);

        RewardLedger r2 = new RewardLedger(userId, 25, "earn", "Duplicate Reward", refId);
        assertThrows(DataIntegrityViolationException.class, () -> {
            rewardLedgerRepository.saveAndFlush(r2);
        });
    }
}
