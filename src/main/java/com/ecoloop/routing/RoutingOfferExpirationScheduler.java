package com.ecoloop.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConditionalOnProperty(prefix = "ecoloop.routing.offer-expiration", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class RoutingOfferExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoutingOfferExpirationScheduler.class);

    private final RoutingOfferRepository offers;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RoutingOfferExpirationScheduler(RoutingOfferRepository offers,
                                           PlatformTransactionManager transactionManager,
                                           @Value("${ecoloop.routing.offer-expiration.batch-size:500}") int batchSize) {
        this.offers = offers;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${ecoloop.routing.offer-expiration.interval-ms:3600000}")
    public void runExpirationCleanup() {
        if (!running.compareAndSet(false, true)) {
            log.info("RoutingOffer expiration cleanup skipped — previous run still in progress");
            return;
        }
        long startedAtMs = System.currentTimeMillis();
        AtomicInteger totalExpired = new AtomicInteger(0);
        try {
            final int[] pageHolder = new int[]{0};
            while (true) {
                final int currentPage = pageHolder[0];
                Integer expiredInBatch = transactionTemplate.execute(status -> {
                    Instant cutoff = Instant.now();
                    Slice<RoutingOffer> slice = offers.findExpiredOffersSliced(
                        cutoff, PageRequest.of(currentPage, batchSize));
                    List<UUID> ids = slice.getContent().stream()
                        .map(RoutingOffer::getId)
                        .toList();
                    if (ids.isEmpty()) {
                        return -1;
                    }
                    int updated = offers.markAsExpiredInBatch(ids);
                    log.debug("Expiration job batch processed: ids={} updated={}", ids.size(), updated);
                    return updated;
                });
                if (expiredInBatch == null || expiredInBatch < 0) {
                    break;
                }
                totalExpired.addAndGet(expiredInBatch);
                if (expiredInBatch < batchSize) {
                    break;
                }
                pageHolder[0]++;
            }
            long durationMs = System.currentTimeMillis() - startedAtMs;
            if (totalExpired.get() > 0) {
                log.info("RoutingOffer expiration cleanup completed: expiredCount={} durationMs={}",
                    totalExpired.get(), durationMs);
            } else {
                log.debug("RoutingOffer expiration cleanup completed: no expired offers durationMs={}", durationMs);
            }
        } catch (Exception ex) {
            log.error("RoutingOffer expiration cleanup failed after expiredCount={}",
                totalExpired.get(), ex);
        } finally {
            running.set(false);
        }
    }

    int runNow() {
        runExpirationCleanup();
        return 0;
    }
}
