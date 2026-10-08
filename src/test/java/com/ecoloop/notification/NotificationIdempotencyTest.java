package com.ecoloop.notification;

import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.pickup.PickupCompletedEvent;
import com.ecoloop.pickup.PickupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("postgres-test")
class NotificationIdempotencyTest {

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

    @Autowired NotificationRepository notifications;
    @Autowired NotificationService service;
    @Autowired UserRepository users;
    @Autowired PickupRepository pickups;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired TransactionTemplate tx;

    private UUID userId;
    private UUID pickupId;
    private com.ecoloop.pickup.PickupRequest pickupEntity;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        pickups.deleteAll();
        users.deleteAllInBatch();
        userId = users.saveAndFlush(new User("idem-" + UUID.randomUUID() + "@ecoloop.test", "hash", "I", "HOUSEHOLD")).getId();
        pickupEntity = new com.ecoloop.pickup.PickupRequest(userId, null, "test addr");
        pickupEntity = pickups.saveAndFlush(pickupEntity);
        pickupId = pickupEntity.getId();
    }

    @Test
    void createSameNotificationTwiceReturnsOneRowOnly() {
        Map<String, Object> params = Map.of("pickupId", pickupId);
        Notification first = service.create(userId, NotificationType.PICKUP_COMPLETED, pickupId, params);
        assertNotNull(first);

        Notification second = service.create(userId, NotificationType.PICKUP_COMPLETED, pickupId, params);
        assertNotNull(second);
        assertEquals(first.getId(), second.getId(), "Idempotent call must return the original persisted row (same id)");

        long count = notifications.count();
        assertEquals(1L, count, "Duplicate create() call must not insert a second row due to (user,type,ref) unique constraint");
    }

    @Test
    void firingPickupCompletedEventTwiceProducesExactlyOneNotificationRow() {
        tx.executeWithoutResult(s -> publisher.publishEvent(new PickupCompletedEvent(pickupId, UUID.randomUUID())));
        await().atMost(3, TimeUnit.SECONDS).until(() -> notifications.count() >= 1);
        // Force the row to exist to avoid flakiness (the above publish created at least one).
        assertEquals(1L, notifications.count(), "First event must produce exactly 1 notification row");

        Notification first = notifications.findAllByUserIdOrderByCreatedAtDesc(userId).get(0);

        tx.executeWithoutResult(s -> publisher.publishEvent(new PickupCompletedEvent(pickupId, UUID.randomUUID())));
        // Give async listeners a window to attempt to insert again.
        try { Thread.sleep(500L); } catch (InterruptedException ignored) {}

        long count = notifications.count();
        assertEquals(1L, count, "Firing duplicate PickupCompletedEvent must still produce exactly 1 notification row");

        Notification stillFirst = notifications.findAllByUserIdOrderByCreatedAtDesc(userId).get(0);
        assertEquals(first.getId(), stillFirst.getId(), "Row id must match original after second event fire");
    }
}
