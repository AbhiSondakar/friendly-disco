package com.ecoloop.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
    long countByUserIdAndReadFalse(UUID userId);
}
