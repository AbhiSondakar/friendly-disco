package com.ecoloop.rewards;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RewardLedgerRepository extends JpaRepository<RewardLedger, UUID> {
    List<RewardLedger> findAllByUserId(UUID userId);
    Optional<RewardLedger> findByUserIdAndType(UUID userId, String type);
    Optional<RewardLedger> findByUserIdAndReferenceId(UUID userId, UUID referenceId);

    @Query("select coalesce(sum(r.points),0) from RewardLedger r where r.userId = :userId")
    int balance(@Param("userId") UUID userId);
}
