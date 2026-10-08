package com.ecoloop.pickup;

import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.security.Role;
import com.ecoloop.device.Device;
import com.ecoloop.device.DeviceRepository;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.rewards.RewardLedger;
import com.ecoloop.rewards.RewardLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PickupServiceTest {

    @Autowired
    private PickupService pickupService;

    @Autowired
    private PickupRepository pickupRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private RewardLedgerRepository rewardLedgerRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID householdUserId;
    private UUID partnerUserId;
    private Partner partner;
    private Device device;
    private PickupRequest pickup;
    private ActorContext partnerActor;

    @BeforeEach
    void setUp() {
        householdUserId = UUID.randomUUID();
        User householdUser = userRepository.save(new User(
            "household." + householdUserId + "@example.com", "hash", "Household User", Role.HOUSEHOLD.name()
        ));
        householdUserId = householdUser.getId();

        partnerUserId = UUID.randomUUID();
        User partnerUser = userRepository.save(new User(
            "partner." + partnerUserId + "@example.com", "hash", "Partner User", Role.PARTNER.name()
        ));
        partnerUserId = partnerUser.getId();

        partner = new Partner(partnerUserId, "Green Logistics", "Recycler", "LIC-GL1");
        partner.setStatus("approved");
        partner.setCapacity(10);
        partner.setRating(BigDecimal.valueOf(4.8));
        partner = partnerRepository.save(partner);

        device = new Device(householdUserId, "good", "laptop", BigDecimal.valueOf(0.8), "ready_for_pickup");
        device = deviceRepository.save(device);

        pickup = new PickupRequest(householdUserId, device.getId(), "100 Green St");
        pickup.setPartnerId(partner.getId());
        pickup.setStatus("accepted");
        pickup.setCreatedAt(Instant.now());
        pickup.setUpdatedAt(Instant.now());
        pickup = pickupRepository.save(pickup);

        partnerActor = new ActorContext(partnerUserId, Role.PARTNER);
    }

    @Test
    @DisplayName("completeAssignedPickup credits exactly 25 points, writes one reward_ledger row, is idempotent on retry")
    void completeAssignedPickupCreditsPointsAndIsIdempotent() {
        // Given initial state
        List<RewardLedger> initialLedgers = rewardLedgerRepository.findAllByUserId(householdUserId);
        assertThat(initialLedgers).isEmpty();

        // When completing the assigned pickup
        PickupRequest completed = pickupService.completeAssignedPickup(partnerActor, pickup.getId());

        // Then pickup is completed
        assertThat(completed.getStatus()).isEqualTo("completed");
        assertThat(completed.getCompletedAt()).isNotNull();

        // Exactly one reward_ledger row is written
        List<RewardLedger> ledgers = rewardLedgerRepository.findAllByUserId(householdUserId);
        assertThat(ledgers).hasSize(1);
        RewardLedger ledgerRow = ledgers.get(0);
        assertThat(ledgerRow.getPoints()).isEqualTo(25);
        assertThat(ledgerRow.getType()).isEqualTo("earn");
        assertThat(ledgerRow.getReferenceId()).isEqualTo(pickup.getId());

        // When calling completeAssignedPickup again on retry
        PickupRequest retried = pickupService.completeAssignedPickup(partnerActor, pickup.getId());

        // Then returns completed idempotently without creating another reward row
        assertThat(retried.getStatus()).isEqualTo("completed");
        List<RewardLedger> ledgersAfterRetry = rewardLedgerRepository.findAllByUserId(householdUserId);
        assertThat(ledgersAfterRetry).hasSize(1);
        assertThat(ledgersAfterRetry.get(0).getPoints()).isEqualTo(25);
    }
}
