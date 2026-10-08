package com.ecoloop.common;

import com.ecoloop.audit.AuditLogRepository;
import com.ecoloop.identity.PasswordResetTokenRepository;
import com.ecoloop.notification.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "ecoloop.retention.enabled", havingValue = "true", matchIfMissing = true)
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);
    private static final int AUDIT_TOKEN_BATCH_SIZE = 5_000;
    private static final int NOTIFICATION_BATCH_SIZE = 10_000;

    private final PasswordResetTokenRepository tokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    private final TransactionTemplate transactionTemplate;
    private final int auditRetentionDays;
    private final int tokenRetentionDays;
    private final int notificationRetentionDays;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DataRetentionScheduler(PasswordResetTokenRepository tokenRepository,
                                  AuditLogRepository auditLogRepository,
                                  NotificationRepository notificationRepository,
                                  PlatformTransactionManager transactionManager,
                                  @Value("${ecoloop.retention.audit-days:365}") int auditRetentionDays,
                                  @Value("${ecoloop.retention.token-days:7}") int tokenRetentionDays,
                                  @Value("${ecoloop.retention.notification-days:90}") int notificationRetentionDays) {
        this.tokenRepository = tokenRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationRepository = notificationRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
        this.auditRetentionDays = Math.max(1, auditRetentionDays);
        this.tokenRetentionDays = Math.max(1, tokenRetentionDays);
        this.notificationRetentionDays = Math.max(1, notificationRetentionDays);
    }

    @Scheduled(fixedDelayString = "${ecoloop.retention.interval-ms:86400000}")
    public void runRetentionCleanup() {
        if (!running.compareAndSet(false, true)) {
            log.info("Data retention cleanup skipped — previous run still in progress");
            return;
        }
        try {
            int tokensPurged = purgePasswordResetTokens();
            int auditPurged = purgeAuditLogs();
            int notificationsPurged = purgeReadNotifications();
            if (tokensPurged > 0 || auditPurged > 0 || notificationsPurged > 0) {
                log.info("Data retention cleanup completed: tokensPurged={} auditLogsPurged={} notificationsPurged={}",
                        tokensPurged, auditPurged, notificationsPurged);
            } else {
                log.debug("Data retention cleanup completed: no expired records to purge");
            }
        } catch (Exception ex) {
            log.error("Data retention cleanup failed", ex);
        } finally {
            running.set(false);
        }
    }

    public int purgePasswordResetTokens() {
        int totalUsedPurged = 0;
        while (true) {
            Integer purged = transactionTemplate.execute(status -> {
                List<UUID> ids = tokenRepository.findUsedTokenIds(PageRequest.of(0, AUDIT_TOKEN_BATCH_SIZE));
                if (ids.isEmpty()) {
                    return 0;
                }
                return tokenRepository.deleteAllByIdIn(ids);
            });
            int batchCount = purged != null ? purged : 0;
            totalUsedPurged += batchCount;
            if (batchCount < AUDIT_TOKEN_BATCH_SIZE) {
                break;
            }
        }

        Instant cutoff = Instant.now().minus(tokenRetentionDays, ChronoUnit.DAYS);
        int totalExpiredPurged = 0;
        while (true) {
            Integer purged = transactionTemplate.execute(status -> {
                List<UUID> ids = tokenRepository.findExpiredTokenIds(cutoff, PageRequest.of(0, AUDIT_TOKEN_BATCH_SIZE));
                if (ids.isEmpty()) {
                    return 0;
                }
                return tokenRepository.deleteAllByIdIn(ids);
            });
            int batchCount = purged != null ? purged : 0;
            totalExpiredPurged += batchCount;
            if (batchCount < AUDIT_TOKEN_BATCH_SIZE) {
                break;
            }
        }

        int totalPurged = totalUsedPurged + totalExpiredPurged;
        if (totalPurged > 0) {
            log.info("Purged password reset tokens: total={}, used={}, expiredBeforeCutoff={}",
                    totalPurged, totalUsedPurged, totalExpiredPurged);
        }
        return totalPurged;
    }

    public int purgeAuditLogs() {
        Instant cutoff = Instant.now().minus(auditRetentionDays, ChronoUnit.DAYS);
        int totalPurged = 0;
        while (true) {
            Integer purged = transactionTemplate.execute(status -> {
                List<UUID> ids = auditLogRepository.findExpiredAuditLogIds(cutoff, PageRequest.of(0, AUDIT_TOKEN_BATCH_SIZE));
                if (ids.isEmpty()) {
                    return 0;
                }
                return auditLogRepository.deleteAllByIdIn(ids);
            });
            int batchCount = purged != null ? purged : 0;
            totalPurged += batchCount;
            if (batchCount < AUDIT_TOKEN_BATCH_SIZE) {
                break;
            }
        }
        if (totalPurged > 0) {
            log.info("Purged audit logs: total={}, cutoff={}", totalPurged, cutoff);
        }
        return totalPurged;
    }

    /**
     * Purges read notifications older than the retention window.
     * Unread notifications are NEVER purged; a user who hasn't opened the app keeps their inbox.
     *
     * @return total number of notification rows deleted
     */
    public int purgeReadNotifications() {
        Instant cutoff = Instant.now().minus(notificationRetentionDays, ChronoUnit.DAYS);
        int totalPurged = 0;
        while (true) {
            Integer purged = transactionTemplate.execute(status -> {
                List<UUID> ids = notificationRepository.findReadOlderThanCutoff(cutoff, NOTIFICATION_BATCH_SIZE);
                if (ids.isEmpty()) {
                    return 0;
                }
                return notificationRepository.deleteAllByIdIn(ids);
            });
            int batchCount = purged != null ? purged : 0;
            totalPurged += batchCount;
            if (batchCount < NOTIFICATION_BATCH_SIZE) {
                break;
            }
        }
        if (totalPurged > 0) {
            log.info("Purged read notifications: total={}, retentionDays={}, cutoff={}",
                totalPurged, notificationRetentionDays, cutoff);
        }
        return totalPurged;
    }
}
