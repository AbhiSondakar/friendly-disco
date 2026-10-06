package com.ecoloop;

import com.ecoloop.routing.RoutingOffer;
import com.ecoloop.routing.RoutingOfferExpirationScheduler;
import com.ecoloop.routing.RoutingOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ecoloop.routing.offer-expiration.enabled=true",
    "ecoloop.routing.offer-expiration.batch-size=5",
    "ecoloop.routing.offer-expiration.interval-ms=3600000"
})
class RoutingOfferExpirationIntegrationTest {

    @Autowired
    RoutingOfferRepository offers;

    @Autowired
    RoutingOfferExpirationScheduler scheduler;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
        tx.execute(s -> {
            offers.deleteAll();
            return null;
        });
        assertNotNull(scheduler, "Scheduler must be wired when enabled=true");
    }

    private List<RoutingOffer> seedOffers(int expiredCount, int futureCount, int noExpiryCount) {
        return tx.execute(s -> {
            List<RoutingOffer> result = new ArrayList<>();
            UUID partnerA = UUID.randomUUID();
            UUID partnerB = UUID.randomUUID();
            for (int i = 0; i < expiredCount; i++) {
                RoutingOffer o = new RoutingOffer(UUID.randomUUID(),
                    i % 2 == 0 ? partnerA : partnerB,
                    Instant.now().minusSeconds(1 + i * 60L));
                o.setScore(0.9);
                result.add(offers.save(o));
            }
            for (int i = 0; i < futureCount; i++) {
                RoutingOffer o = new RoutingOffer(UUID.randomUUID(),
                    i % 2 == 0 ? partnerA : partnerB,
                    Instant.now().plusSeconds(3600 + i * 60L));
                result.add(offers.save(o));
            }
            for (int i = 0; i < noExpiryCount; i++) {
                RoutingOffer o = new RoutingOffer(UUID.randomUUID(),
                    i % 2 == 0 ? partnerA : partnerB, null);
                result.add(offers.save(o));
            }
            return result;
        });
    }

    @Test
    void schedulerExpiresStaleOffersAndLeavesFutureUntouched() {
        final int expiredCount = 23;
        final int futureCount = 7;
        final int noExpiryCount = 3;
        seedOffers(expiredCount, futureCount, noExpiryCount);

        scheduler.runExpirationCleanup();

        tx.execute(s -> {
            List<RoutingOffer> all = offers.findAll();
            long actualExpired = all.stream()
                .filter(o -> "expired".equals(o.getStatus())).count();
            long stillOffered = all.stream()
                .filter(o -> "offered".equals(o.getStatus())).count();
            assertEquals(expiredCount, actualExpired,
                "All " + expiredCount + " past-expiry offers must now have status=expired (batch size=5)");
            assertEquals(futureCount + noExpiryCount, stillOffered,
                "Future + no-expiry offers must stay offered");
            return null;
        });
    }

    @Test
    void schedulerIsIdempotentAcrossRepeatedRuns() {
        seedOffers(11, 4, 2);

        scheduler.runExpirationCleanup();
        long afterFirstRun = tx.execute(s ->
            offers.findAll().stream()
                .filter(o -> "expired".equals(o.getStatus())).count());
        assertEquals(11, afterFirstRun);

        scheduler.runExpirationCleanup();
        scheduler.runExpirationCleanup();

        long afterThirdRun = tx.execute(s ->
            offers.findAll().stream()
                .filter(o -> "expired".equals(o.getStatus())).count());
        assertEquals(11, afterThirdRun,
            "Repeated scheduler runs must not change counts after all expired are marked");
    }

    @Test
    void schedulerDoesNotAlterAlreadyNonOfferedStatuses() {
        UUID pid = UUID.randomUUID();
        tx.execute(s -> {
            RoutingOffer expiredPast = new RoutingOffer(UUID.randomUUID(), pid,
                Instant.now().minusSeconds(3600));
            expiredPast.setStatus("accepted");
            offers.save(expiredPast);

            RoutingOffer stalePending = new RoutingOffer(UUID.randomUUID(), pid,
                Instant.now().minusSeconds(3600));
            stalePending.setStatus("rejected");
            offers.save(stalePending);

            RoutingOffer staleCancelled = new RoutingOffer(UUID.randomUUID(), pid,
                Instant.now().minusSeconds(3600));
            staleCancelled.setStatus("superseded");
            offers.save(staleCancelled);

            RoutingOffer actuallyStaleOffered = new RoutingOffer(UUID.randomUUID(), pid,
                Instant.now().minusSeconds(120));
            offers.save(actuallyStaleOffered);
            return null;
        });

        scheduler.runExpirationCleanup();

        tx.execute(s -> {
            List<RoutingOffer> all = offers.findAll();
            long accepted = all.stream().filter(o -> "accepted".equals(o.getStatus())).count();
            long rejected = all.stream().filter(o -> "rejected".equals(o.getStatus())).count();
            long superseded = all.stream().filter(o -> "superseded".equals(o.getStatus())).count();
            long expired = all.stream().filter(o -> "expired".equals(o.getStatus())).count();
            assertEquals(1, accepted, "Accepted status must remain even if expiresAt passed");
            assertEquals(1, rejected, "Rejected status must remain");
            assertEquals(1, superseded, "Superseded status must remain");
            assertEquals(1, expired, "Only the truly stale 'offered' row transitions to expired");
            return null;
        });
    }

    @Test
    void overlappingRunsAreSkippedGracefully() throws Exception {
        seedOffers(5, 0, 0);
        int firstResult = scheduler.runNow();
        assertEquals(0, firstResult);

        long expiredAfter = tx.execute(s ->
            offers.findAll().stream()
                .filter(o -> "expired".equals(o.getStatus())).count());
        assertEquals(5, expiredAfter);
    }

    @Test
    void highVolumeExpirationHandledWithinBatching() {
        final int hugeBatch = 107;
        seedOffers(hugeBatch, 0, 0);

        scheduler.runExpirationCleanup();

        long expired = tx.execute(s ->
            offers.findAll().stream()
                .filter(o -> "expired".equals(o.getStatus())).count());
        assertEquals(hugeBatch, expired,
            "Scheduler must page through all 107 stale offers using batch-size=5, none left behind");
    }
}
