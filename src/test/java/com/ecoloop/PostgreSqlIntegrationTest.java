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

    @Autowired
    private com.ecoloop.identity.PushTokenRepository pushTokenRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void postgresBootstrapAndFlywayValidated() {
        assertNotNull(userRepository);
    }

    @Test
    void pickupStatusConstraintRejectsRemovedInProgressState() {
        User user = userRepository.saveAndFlush(new User(
            "pickup-state." + UUID.randomUUID() + "@ecoloop.test", "hash", "State test", "HOUSEHOLD"));
        PickupRequest pickup = new PickupRequest(user.getId(), null, "1 State Way");
        pickup.setStatus("in_progress");

        assertThrows(DataIntegrityViolationException.class, () -> pickupRepository.saveAndFlush(pickup));
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

    @Test
    void partialUniquePhoneAllowsMultipleNullsButRejectsDuplicateNonNull() {
        // Multiple users with null phone must succeed
        User u1 = new User("nullphone1@ecoloop.test", "hash", "Null Phone 1", "HOUSEHOLD");
        u1.setPhone(null);
        userRepository.saveAndFlush(u1);

        User u2 = new User("nullphone2@ecoloop.test", "hash", "Null Phone 2", "HOUSEHOLD");
        u2.setPhone(null);
        userRepository.saveAndFlush(u2);

        // First user with phone succeeds
        String phone = "+12065550199";
        User u3 = new User("phone1@ecoloop.test", "hash", "Phone 1", "HOUSEHOLD");
        u3.setPhone(phone);
        userRepository.saveAndFlush(u3);

        // Second user with same non-null phone must be rejected by uq_users_phone
        User u4 = new User("phone2@ecoloop.test", "hash", "Phone 2", "HOUSEHOLD");
        u4.setPhone(phone);
        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(u4);
        });
    }

    @Test
    void pushTokensTokenUniquenessRejectsDuplicateAcrossUsers() {
        String token = "push-token-" + UUID.randomUUID();
        User u1 = userRepository.saveAndFlush(new User("push1." + UUID.randomUUID() + "@ecoloop.test", "hash", "P1", "HOUSEHOLD"));
        User u2 = userRepository.saveAndFlush(new User("push2." + UUID.randomUUID() + "@ecoloop.test", "hash", "P2", "HOUSEHOLD"));

        pushTokenRepository.saveAndFlush(new com.ecoloop.identity.PushToken(u1.getId(), token, "ios"));

        // Second user registering identical push token must be rejected by uq_push_tokens_token
        assertThrows(DataIntegrityViolationException.class, () -> {
            pushTokenRepository.saveAndFlush(new com.ecoloop.identity.PushToken(u2.getId(), token, "android"));
        });
    }

    @Test
    void foreignKeyOnDeleteRestrictPreventsDeletingUserWithRewardLedger() {
        User u = userRepository.saveAndFlush(new User("restrict.ledger." + UUID.randomUUID() + "@ecoloop.test", "hash", "R1", "HOUSEHOLD"));
        rewardLedgerRepository.saveAndFlush(new RewardLedger(u.getId(), 50, "earn", "Points", UUID.randomUUID()));

        // Deleting user must fail due to fk_reward_ledger_user ON DELETE RESTRICT
        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.deleteById(u.getId());
            userRepository.flush();
        });
    }

    @Test
    void foreignKeyOnDeleteRestrictPreventsDeletingUserWithPickupRequests() {
        User u = userRepository.saveAndFlush(new User("restrict.pickup." + UUID.randomUUID() + "@ecoloop.test", "hash", "R2", "HOUSEHOLD"));
        PickupRequest pickup = new PickupRequest(u.getId(), null, "123 Green Way");
        pickupRepository.saveAndFlush(pickup);

        // Deleting user must fail due to fk_pickup_requests_user ON DELETE RESTRICT
        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.deleteById(u.getId());
            userRepository.flush();
        });
    }

    @Test
    void instantPersistedToTimestamptzPreservesMillisecondPrecisionOnPostgres() {
        Instant pinnedInstant = Instant.parse("2026-10-08T15:30:45.678Z");

        User user = new User("timestamptz.pg." + UUID.randomUUID() + "@ecoloop.test", "hash", "Timestamp User PG", "HOUSEHOLD");
        user.setCreatedAt(pinnedInstant);
        user.setUpdatedAt(pinnedInstant);

        User saved = userRepository.saveAndFlush(user);
        assertNotNull(saved.getId());

        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(reloaded.getCreatedAt());

        assertEquals(pinnedInstant.toEpochMilli(), reloaded.getCreatedAt().toEpochMilli(),
                "Persisted Instant to PostgreSQL TIMESTAMPTZ must round-trip with exact millisecond epoch equality");
        assertEquals(pinnedInstant.truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
                reloaded.getCreatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
                "Persisted Instant must match original truncated to milliseconds on Postgres");
    }
}
