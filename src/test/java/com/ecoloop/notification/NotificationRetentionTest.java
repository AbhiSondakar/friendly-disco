package com.ecoloop.notification;

import com.ecoloop.common.DataRetentionScheduler;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
class NotificationRetentionTest {

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

    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired DataRetentionScheduler scheduler;

    private UUID userId1;
    private UUID userId2;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        users.deleteAllInBatch();
        userId1 = users.saveAndFlush(new User("ret1-" + UUID.randomUUID() + "@ecoloop.test", "hash", "R1", "HOUSEHOLD")).getId();
        userId2 = users.saveAndFlush(new User("ret2-" + UUID.randomUUID() + "@ecoloop.test", "hash", "R2", "HOUSEHOLD")).getId();
    }

    @Test
    void readOldNotificationsArePurgedAfterRetentionWindow() {
        // 180 days old, is_read = true → should be purged
        Notification n1 = new Notification(userId1, NotificationType.PICKUP_COMPLETED, "t", "b", UUID.randomUUID());
        n1.setRead(true);
        n1.setCreatedAt(Instant.now().minus(180, ChronoUnit.DAYS));
        notifications.saveAndFlush(n1);

        Notification n2 = new Notification(userId2, NotificationType.PICKUP_ACCEPTED, "t", "b", UUID.randomUUID());
        n2.setRead(true);
        n2.setCreatedAt(Instant.now().minus(365, ChronoUnit.DAYS));
        notifications.saveAndFlush(n2);

        long before = notifications.count();
        assertEquals(2L, before);

        int purged = scheduler.purgeReadNotifications();
        assertEquals(2, purged, "Old read notifications should be purged in one batch");
        assertEquals(0L, notifications.count());
    }

    @Test
    void unreadOldNotificationsAreNeverPurged() {
        // unread + very old → retention policy must NOT remove it (users who haven't opened the app keep inbox entries).
        Notification unreadOld = new Notification(userId1, NotificationType.PICKUP_ACCEPTED, "t", "b", UUID.randomUUID());
        unreadOld.setRead(false);
        unreadOld.setCreatedAt(Instant.now().minus(365 * 2, ChronoUnit.DAYS));
        notifications.saveAndFlush(unreadOld);

        int purged = scheduler.purgeReadNotifications();
        assertEquals(0, purged, "Unread notifications are never purged even if very old");

        Notification stillExists = notifications.findById(unreadOld.getId()).orElseThrow();
        assertFalse(stillExists.isRead());
    }

    @Test
    void recentReadNotificationsAreNotPurged() {
        // Read but only 3 days old; default retention is 90 days so these must be retained
        Notification recentRead = new Notification(userId1, NotificationType.PICKUP_VERIFIED, "t", "b", UUID.randomUUID());
        recentRead.setRead(true);
        recentRead.setCreatedAt(Instant.now().minus(3, ChronoUnit.DAYS));
        notifications.saveAndFlush(recentRead);

        int purged = scheduler.purgeReadNotifications();
        assertEquals(0, purged, "Read notifications newer than retention window must be kept");

        // Also verify one mixed case
        Notification oldRead = new Notification(userId1, NotificationType.PICKUP_REOFFERED, "t", "b", UUID.randomUUID());
        oldRead.setRead(true);
        oldRead.setCreatedAt(Instant.now().minus(120, ChronoUnit.DAYS));
        notifications.saveAndFlush(oldRead);

        int purgedMixed = scheduler.purgeReadNotifications();
        assertEquals(1, purgedMixed, "Only the old-read row must be purged; recent-read retained");
        assertTrue(notifications.findById(recentRead.getId()).isPresent(), "Recent-read retained");
        assertTrue(notifications.findById(oldRead.getId()).isEmpty(), "Old-read purged");
    }
}
