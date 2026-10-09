package com.ecoloop.notification;

import com.ecoloop.identity.NotificationPreference;
import com.ecoloop.identity.NotificationPreferenceRepository;
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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("postgres-test")
class NotificationPreferenceTest {

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
    @Autowired NotificationPreferenceRepository preferences;
    @Autowired NotificationService service;
    @Autowired NotificationListener listener;

    private UUID householdId;
    private UUID pickupId;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        preferences.deleteAll();
        users.deleteAllInBatch();
        householdId = users.saveAndFlush(new User("pref-" + UUID.randomUUID() + "@ecoloop.test", "hash", "P", "HOUSEHOLD")).getId();
        pickupId = UUID.randomUUID();
    }

    @Test
    void rowAlwaysWrittenEvenWhenPreferenceIsDisabled() {
        NotificationPreference pref = new NotificationPreference(householdId);
        pref.setPickupUpdates(false);
        
        preferences.saveAndFlush(pref);

        Notification n = service.create(householdId, NotificationType.PICKUP_ACCEPTED, pickupId, Map.of("pickupId", pickupId));
        assertNotNull(n);
        assertFalse(n.isRead(), "New notification starts as unread");

        List<Notification> all = notifications.findAllByUserIdOrderByCreatedAtDesc(householdId);
        assertEquals(1, all.size(), "Preference OFF must not suppress creation (row still written to inbox)");
    }

    @Test
    void missingPreferenceDefaultsToEnabledForPickupUpdates() throws Exception {
        NotificationListener.GatePredicate gateForAccepted = extractGate(NotificationType.PICKUP_ACCEPTED);
        // No preference row exists at all → gate should be TRUE (default enabled).
        assertTrue(gateForAccepted.isDeliveryEnabledFor(null),
            "Missing preference row must default to enabled=true for pickup_updates type (PICKUP_ACCEPTED)");
    }

    @Test
    void disabledPickupUpdatesGateSuppressSendButRowStillExists() throws Exception {
        NotificationPreference pref = new NotificationPreference(householdId);
        pref.setPickupUpdates(false);
        preferences.saveAndFlush(pref);

        // Row still written (spec: preferences checked at SEND time, not CREATE time).
        Notification n = service.create(householdId, NotificationType.PICKUP_ACCEPTED, pickupId, Map.of());
        assertNotNull(n);

        NotificationListener.GatePredicate gate = extractGate(NotificationType.PICKUP_ACCEPTED);
        boolean gateResult = gate.isDeliveryEnabledFor(pref);
        assertFalse(gateResult, "pickup_updates=false must close the gate for PICKUP_ACCEPTED send decision");

        // Despite gate being closed, the row is there
        assertEquals(1L, notifications.count(), "Creation is never gated; only external send is");
    }

    private NotificationListener.GatePredicate extractGate(NotificationType type) throws Exception {
        java.lang.reflect.Method m = NotificationListener.class.getDeclaredMethod("gate", NotificationType.class);
        m.setAccessible(true);
        return (NotificationListener.GatePredicate) m.invoke(listener, type);
    }
}
