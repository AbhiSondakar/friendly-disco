package com.ecoloop.common;

import com.ecoloop.audit.AuditLog;
import com.ecoloop.audit.AuditLogRepository;
import com.ecoloop.identity.PasswordResetToken;
import com.ecoloop.identity.PasswordResetTokenRepository;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.notification.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DataRetentionSchedulerTest {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private DataRetentionScheduler scheduler;
    private User testUser;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(new User("retention@ecoloop.test", "hash", "Retention User", "HOUSEHOLD"));

        // Instantiate scheduler directly with 365-day audit retention, 7-day token retention, 90-day notification retention
        scheduler = new DataRetentionScheduler(tokenRepository, auditLogRepository, notificationRepository, transactionManager, 365, 7, 90);
    }

    @Test
    void purgePasswordResetTokensDeletesUsedTokensAndTokensExpiredOlderThan7Days() {
        Instant now = Instant.now();

        // 1. Token used 1 hour ago -> Should be purged
        PasswordResetToken usedToken = new PasswordResetToken(testUser.getId(), "sel-1", "hash-1", now.plus(1, ChronoUnit.HOURS));
        usedToken.setUsedAt(now.minus(1, ChronoUnit.HOURS));
        usedToken = tokenRepository.save(usedToken);

        // 2. Token expired 10 days ago (> 7 days) -> Should be purged
        PasswordResetToken oldExpiredToken = new PasswordResetToken(testUser.getId(), "sel-2", "hash-2", now.minus(10, ChronoUnit.DAYS));
        oldExpiredToken = tokenRepository.save(oldExpiredToken);

        // 3. Token expired 2 days ago (< 7 days) and unused -> Should be kept
        PasswordResetToken recentlyExpiredToken = new PasswordResetToken(testUser.getId(), "sel-3", "hash-3", now.minus(2, ChronoUnit.DAYS));
        recentlyExpiredToken = tokenRepository.save(recentlyExpiredToken);

        // 4. Token valid, expires in future -> Should be kept
        PasswordResetToken activeToken = new PasswordResetToken(testUser.getId(), "sel-4", "hash-4", now.plus(1, ChronoUnit.DAYS));
        activeToken = tokenRepository.save(activeToken);

        int purgedCount = scheduler.purgePasswordResetTokens();
        assertEquals(2, purgedCount, "Should purge exactly the 2 qualifying tokens (used or expired >7d)");

        assertFalse(tokenRepository.existsById(usedToken.getId()), "Used token must be purged");
        assertFalse(tokenRepository.existsById(oldExpiredToken.getId()), "Old expired token must be purged");
        assertTrue(tokenRepository.existsById(recentlyExpiredToken.getId()), "Recently expired token (<7d) must be retained");
        assertTrue(tokenRepository.existsById(activeToken.getId()), "Active token must be retained");
    }

    @Test
    void purgeAuditLogsDeletesLogsOlderThanConfiguredRetention() {
        Instant now = Instant.now();

        // 1. Audit log created 400 days ago (> 365 days retention) -> Should be purged
        AuditLog oldLog = new AuditLog(testUser.getId(), "HOUSEHOLD", "LOGIN", "user", testUser.getId(), "SUCCESS", null);
        oldLog.setCreatedAt(now.minus(400, ChronoUnit.DAYS));
        oldLog = auditLogRepository.save(oldLog);

        // 2. Audit log created 30 days ago (< 365 days retention) -> Should be kept
        AuditLog recentLog = new AuditLog(testUser.getId(), "HOUSEHOLD", "DEVICE_SUBMIT", "device", UUID.randomUUID(), "SUCCESS", null);
        recentLog.setCreatedAt(now.minus(30, ChronoUnit.DAYS));
        recentLog = auditLogRepository.save(recentLog);

        int purgedCount = scheduler.purgeAuditLogs();
        assertEquals(1, purgedCount, "Should purge exactly 1 audit log older than 365 days");

        assertFalse(auditLogRepository.existsById(oldLog.getId()), "Old audit log must be purged");
        assertTrue(auditLogRepository.existsById(recentLog.getId()), "Recent audit log must be retained");
    }

    @Test
    void purgeReadNotificationsDeletesOnlyReadNotificationsOlderThanRetentionWindow() {
        Instant now = Instant.now();

        // 1. Read notification created 120 days ago (> 90 days retention) -> Should be purged
        com.ecoloop.notification.Notification oldRead = new com.ecoloop.notification.Notification(
            testUser.getId(), com.ecoloop.notification.NotificationType.PICKUP_COMPLETED, "Old Read", "Body", UUID.randomUUID());
        oldRead.setRead(true);
        oldRead.setCreatedAt(now.minus(120, ChronoUnit.DAYS));
        oldRead = notificationRepository.save(oldRead);

        // 2. Unread notification created 120 days ago (> 90 days retention) -> MUST BE RETAINED (unread never purged)
        com.ecoloop.notification.Notification oldUnread = new com.ecoloop.notification.Notification(
            testUser.getId(), com.ecoloop.notification.NotificationType.PICKUP_COMPLETED, "Old Unread", "Body", UUID.randomUUID());
        oldUnread.setRead(false);
        oldUnread.setCreatedAt(now.minus(120, ChronoUnit.DAYS));
        oldUnread = notificationRepository.save(oldUnread);

        // 3. Read notification created 30 days ago (< 90 days retention) -> Should be kept
        com.ecoloop.notification.Notification recentRead = new com.ecoloop.notification.Notification(
            testUser.getId(), com.ecoloop.notification.NotificationType.PICKUP_COMPLETED, "Recent Read", "Body", UUID.randomUUID());
        recentRead.setRead(true);
        recentRead.setCreatedAt(now.minus(30, ChronoUnit.DAYS));
        recentRead = notificationRepository.save(recentRead);

        int purgedCount = scheduler.purgeReadNotifications();
        assertEquals(1, purgedCount, "Should purge exactly 1 read notification older than 90 days");

        assertFalse(notificationRepository.existsById(oldRead.getId()), "Old read notification must be purged");
        assertTrue(notificationRepository.existsById(oldUnread.getId()), "Old unread notification must be retained");
        assertTrue(notificationRepository.existsById(recentRead.getId()), "Recent read notification must be retained");
    }
}
