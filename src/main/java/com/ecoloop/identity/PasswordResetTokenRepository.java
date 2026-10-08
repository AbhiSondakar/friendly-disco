package com.ecoloop.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.selector = :selector")
    Optional<PasswordResetToken> findBySelectorForUpdate(@Param("selector") String selector);

    void deleteAllByUserId(UUID userId);

    @Query("SELECT t.id FROM PasswordResetToken t WHERE t.usedAt IS NOT NULL ORDER BY t.id ASC")
    List<UUID> findUsedTokenIds(Pageable pageable);

    @Query("SELECT t.id FROM PasswordResetToken t WHERE t.expiresAt < :cutoff AND t.usedAt IS NULL ORDER BY t.id ASC")
    List<UUID> findExpiredTokenIds(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.id IN :ids")
    int deleteAllByIdIn(@Param("ids") List<UUID> ids);
}
