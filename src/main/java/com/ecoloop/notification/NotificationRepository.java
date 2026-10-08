package com.ecoloop.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<Notification> findAllByUserId(UUID userId, Pageable pageable);
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
    long countByUserIdAndReadFalse(UUID userId);

    Optional<Notification> findByUserIdAndTypeAndReferenceId(UUID userId, NotificationType type, UUID referenceId);

    @Query("SELECT n.id FROM Notification n WHERE n.read = true AND n.createdAt < :cutoff ORDER BY n.createdAt ASC, n.id ASC LIMIT :limit")
    List<UUID> findReadOlderThanCutoff(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id IN :ids")
    int deleteAllByIdIn(@Param("ids") List<UUID> ids);
}
