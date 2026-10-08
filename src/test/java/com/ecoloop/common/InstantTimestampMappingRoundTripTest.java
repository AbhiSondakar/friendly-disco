package com.ecoloop.common;

import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class InstantTimestampMappingRoundTripTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void instantPersistedToTimestamptzPreservesMillisecondPrecisionOnH2() {
        // Pin an instant with explicit millisecond precision: 2026-10-08T15:30:45.678Z
        Instant pinnedInstant = Instant.parse("2026-10-08T15:30:45.678Z");

        User user = new User("timestamptz.h2." + UUID.randomUUID() + "@ecoloop.test", "hash", "Timestamp User", "HOUSEHOLD");
        user.setCreatedAt(pinnedInstant);
        user.setUpdatedAt(pinnedInstant);

        User saved = userRepository.saveAndFlush(user);
        assertNotNull(saved.getId());

        // Evict from first-level cache to force fresh SQL SELECT from database
        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(reloaded.getCreatedAt());

        assertEquals(pinnedInstant.toEpochMilli(), reloaded.getCreatedAt().toEpochMilli(),
                "Persisted Instant to TIMESTAMPTZ must round-trip with exact millisecond epoch equality");
        assertEquals(pinnedInstant.truncatedTo(ChronoUnit.MILLIS), reloaded.getCreatedAt().truncatedTo(ChronoUnit.MILLIS),
                "Persisted Instant must match original truncated to milliseconds");
    }
}
