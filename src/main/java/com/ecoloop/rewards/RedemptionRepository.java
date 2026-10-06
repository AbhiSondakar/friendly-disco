package com.ecoloop.rewards;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RedemptionRepository extends JpaRepository<Redemption, UUID> {
    List<Redemption> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
