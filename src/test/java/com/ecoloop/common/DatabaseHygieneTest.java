package com.ecoloop.common;

import com.ecoloop.identity.PushToken;
import com.ecoloop.identity.PushTokenRepository;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DatabaseHygieneTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        pushTokenRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.saveAndFlush(new User("user1@ecoloop.test", "hash", "User 1", "HOUSEHOLD"));
        user2 = userRepository.saveAndFlush(new User("user2@ecoloop.test", "hash", "User 2", "HOUSEHOLD"));
    }

    @Test
    void pushTokensTokenMustBeUniqueAcrossUsers() {
        String token = "device-token-abc-123";

        pushTokenRepository.saveAndFlush(new PushToken(user1.getId(), token, "ios"));

        // Attempting to register the same device token under a second user must fail uniqueness constraint
        assertThrows(DataIntegrityViolationException.class, () -> {
            pushTokenRepository.saveAndFlush(new PushToken(user2.getId(), token, "android"));
        });
    }

    @Test
    void softDeleteMarksTimestampAndPreservesDatabaseRow() {
        user1.softDelete();
        user1 = userRepository.saveAndFlush(user1);

        assertTrue(user1.isDeleted());
        assertNotNull(user1.getDeletedAt());
        assertFalse(user1.isActive());

        // Row still exists in database
        assertTrue(userRepository.findById(user1.getId()).isPresent());
    }
}
