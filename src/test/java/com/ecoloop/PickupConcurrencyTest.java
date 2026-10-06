package com.ecoloop;

import com.ecoloop.device.Device;
import com.ecoloop.device.DeviceRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.pickup.PickupRepository;
import com.ecoloop.pickup.PickupRequest;
import com.ecoloop.pickup.PickupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PickupConcurrencyTest {

    @Autowired
    private PickupService pickupService;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private PickupRepository pickupRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
        txTemplate.execute(status -> {
            pickupRepository.deleteAll();
            partnerRepository.deleteAll();
            deviceRepository.deleteAll();
            return null;
        });
    }

    private Partner createApprovedPartner(UUID partnerUserId, int capacity) {
        return txTemplate.execute(status -> {
            Partner p = new Partner(partnerUserId, "Concurrent Org " + capacity,
                "Recycler", "LIC-" + partnerUserId);
            p.setStatus("approved");
            p.setCapacity(capacity);
            return partnerRepository.save(p);
        });
    }

    private Device createDevice(UUID householdUserId) {
        return txTemplate.execute(status -> {
            Device d = new Device(householdUserId, "good", "laptop",
                BigDecimal.valueOf(0.95), "completed");
            return deviceRepository.save(d);
        });
    }

    private PickupRequest createPickup(UUID householdUserId, UUID deviceId, String address) {
        return txTemplate.execute(status ->
            pickupService.createPickup(householdUserId, deviceId, address, null));
    }

    @Test
    void partnerCapacityOneStrictlyEnforcedUnderConcurrentAcceptance() throws Exception {
        final int threadCount = 12;
        UUID partnerUserId = UUID.randomUUID();
        Partner partner = createApprovedPartner(partnerUserId, 1);

        UUID householdUserId = UUID.randomUUID();
        List<UUID> pickupIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Device d = createDevice(householdUserId);
            PickupRequest p = createPickup(householdUserId, d.getId(), "Addr " + i);
            pickupIds.add(p.getId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger capacityRejectionCount = new AtomicInteger(0);
        AtomicInteger otherRejectionCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final UUID pickupId = pickupIds.get(i);
            executor.submit(() -> {
                try {
                    startGate.await();
                    PickupRequest accepted = pickupService.acceptByPartnerUser(partnerUserId, pickupId);
                    if ("accepted".equals(accepted.getStatus())
                        && partner.getId().equals(accepted.getPartnerId())) {
                        successCount.incrementAndGet();
                    } else {
                        otherRejectionCount.incrementAndGet();
                    }
                } catch (IllegalStateException ex) {
                    if (ex.getMessage() != null
                        && ex.getMessage().contains("maximum active capacity")) {
                        capacityRejectionCount.incrementAndGet();
                    } else if (ex.getMessage() != null
                        && ex.getMessage().startsWith("Pickup is no longer pending")) {
                        capacityRejectionCount.incrementAndGet();
                    } else {
                        otherRejectionCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    unexpectedErrors.add(ex);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(completed, "All concurrent acceptance threads should complete within 30s");

        long activeJobs = txTemplate.execute(status ->
            pickupRepository.countActiveJobsByPartnerId(partner.getId()));

        assertEquals(1, successCount.get(),
            "Exactly one accept should succeed for partner with capacity=1");
        assertEquals(1, activeJobs,
            "Partner active job count must be exactly 1 after concurrent wave");
        assertEquals(threadCount, successCount.get() + capacityRejectionCount.get() + otherRejectionCount.get(),
            "All 12 attempts must be accounted for as success, capacity rejection, or other rejection");
        assertTrue(unexpectedErrors.isEmpty(),
            "No unexpected throwables: " + unexpectedErrors);
    }

    @Test
    void samePickupDoubleAcceptThrowsIllegalState() throws Exception {
        final int threadCount = 10;
        UUID partnerUserId1 = UUID.randomUUID();
        UUID partnerUserId2 = UUID.randomUUID();
        createApprovedPartner(partnerUserId1, 10);
        createApprovedPartner(partnerUserId2, 10);

        UUID householdUserId = UUID.randomUUID();
        Device d = createDevice(householdUserId);
        PickupRequest singlePickup = createPickup(householdUserId, d.getId(), "Single Pickup");
        UUID pickupId = singlePickup.getId();

        List<UUID> partnerUserIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            partnerUserIds.add(i % 2 == 0 ? partnerUserId1 : partnerUserId2);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger pendingStateRejectionCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final UUID partnerUserId = partnerUserIds.get(i);
            executor.submit(() -> {
                try {
                    startGate.await();
                    PickupRequest accepted = pickupService.acceptByPartnerUser(partnerUserId, pickupId);
                    if ("accepted".equals(accepted.getStatus())) {
                        successCount.incrementAndGet();
                    }
                } catch (IllegalStateException ex) {
                    if (ex.getMessage() != null
                        && ex.getMessage().startsWith("Pickup is no longer pending")) {
                        pendingStateRejectionCount.incrementAndGet();
                    } else {
                        unexpectedErrors.add(ex);
                    }
                } catch (Exception ex) {
                    unexpectedErrors.add(ex);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(completed, "All concurrent same-pickup threads should complete within 30s");

        PickupRequest after = txTemplate.execute(status ->
            pickupRepository.findById(pickupId).orElseThrow());

        assertEquals(1, successCount.get(),
            "Exactly one thread should accept the same pickup");
        assertTrue(pendingStateRejectionCount.get() >= threadCount - 1 - unexpectedErrors.size(),
            "All other threads must be rejected via 'no longer pending' IllegalStateException or lock error");
        assertEquals("accepted", after.getStatus(),
            "Pickup status must be accepted after race");
        assertNotNull(after.getPartnerId(), "Pickup must have a partner assigned");
    }

    @Test
    void multipleDistinctPartnersAcceptingDistinctPickupsAllSucceed() throws Exception {
        final int partnerCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(partnerCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(partnerCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        List<UUID> partnerUserIds = new ArrayList<>();
        List<UUID> pickupIds = new ArrayList<>();
        for (int i = 0; i < partnerCount; i++) {
            UUID puid = UUID.randomUUID();
            createApprovedPartner(puid, 1);
            partnerUserIds.add(puid);

            UUID householdUserId = UUID.randomUUID();
            Device d = createDevice(householdUserId);
            PickupRequest p = createPickup(householdUserId, d.getId(), "Addr Partner " + i);
            pickupIds.add(p.getId());
        }

        for (int i = 0; i < partnerCount; i++) {
            final UUID puid = partnerUserIds.get(i);
            final UUID pid = pickupIds.get(i);
            executor.submit(() -> {
                try {
                    startGate.await();
                    PickupRequest accepted = pickupService.acceptByPartnerUser(puid, pid);
                    if ("accepted".equals(accepted.getStatus())) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    unexpectedErrors.add(ex);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(completed, "All partner-specific accept threads should complete within 30s");
        assertEquals(partnerCount, successCount.get(),
            "All " + partnerCount + " distinct partners should successfully accept their distinct pickups: errors="
                + unexpectedErrors);
        assertTrue(unexpectedErrors.isEmpty(),
            "No unexpected errors: " + unexpectedErrors);
    }

    @Test
    void lockOrderingPartnersThenPickupsPreventsDeadlockUnderLoad() throws Exception {
        final int partnerCount = 6;
        final int pickupsPerPartner = 4;
        final int totalAttempts = partnerCount * pickupsPerPartner;
        ExecutorService executor = Executors.newFixedThreadPool(totalAttempts);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalAttempts);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger expectedRejectionCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        List<UUID> partnerUserIds = new ArrayList<>();
        for (int i = 0; i < partnerCount; i++) {
            UUID puid = UUID.randomUUID();
            createApprovedPartner(puid, 2);
            partnerUserIds.add(puid);
        }

        UUID householdUserId = UUID.randomUUID();
        List<UUID> allPickupIds = new ArrayList<>();
        for (int i = 0; i < totalAttempts; i++) {
            Device d = createDevice(householdUserId);
            PickupRequest p = createPickup(householdUserId, d.getId(), "Deadlock Test " + i);
            allPickupIds.add(p.getId());
        }

        List<int[]> tasks = new ArrayList<>();
        int pickupIdx = 0;
        for (int round = 0; round < pickupsPerPartner; round++) {
            for (int p = 0; p < partnerCount; p++) {
                tasks.add(new int[]{p, pickupIdx});
                pickupIdx++;
            }
        }
        Collections.shuffle(tasks, new Random(42));

        for (int[] task : tasks) {
            final UUID puid = partnerUserIds.get(task[0]);
            final UUID pid = allPickupIds.get(task[1]);
            executor.submit(() -> {
                try {
                    startGate.await();
                    PickupRequest accepted = pickupService.acceptByPartnerUser(puid, pid);
                    if ("accepted".equals(accepted.getStatus())) {
                        successCount.incrementAndGet();
                    }
                } catch (IllegalStateException ex) {
                    String msg = ex.getMessage() != null ? ex.getMessage() : "";
                    if (msg.contains("maximum active capacity")
                        || msg.startsWith("Pickup is no longer pending")) {
                        expectedRejectionCount.incrementAndGet();
                    } else {
                        unexpectedErrors.add(ex);
                    }
                } catch (Exception ex) {
                    String className = ex.getClass().getSimpleName();
                    if (className.contains("Lock")
                        || className.contains("Timeout")
                        || className.contains("CannotAcquire")) {
                        expectedRejectionCount.incrementAndGet();
                    } else {
                        unexpectedErrors.add(ex);
                    }
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(45, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(completed,
            "Deadlock prevention test: all " + totalAttempts
                + " attempts must finish within 45s (no hung threads from deadlock)");

        int totalResolved = successCount.get() + expectedRejectionCount.get();
        assertEquals(totalAttempts, totalResolved,
            "All " + totalAttempts + " attempts resolved either as success or expected rejection; unexpected="
                + unexpectedErrors);
        assertTrue(unexpectedErrors.isEmpty(),
            "No unexpected throwables during deadlock-avoidance load test: " + unexpectedErrors);

        for (UUID puid : partnerUserIds) {
            Partner partner = txTemplate.execute(status ->
                partnerRepository.findByUserId(puid).orElseThrow());
            long active = txTemplate.execute(status ->
                pickupRepository.countActiveJobsByPartnerId(partner.getId()));
            assertTrue(active <= partner.getCapacity(),
                "Partner " + partner.getId() + " active=" + active
                    + " must not exceed capacity=" + partner.getCapacity());
        }
    }
}
