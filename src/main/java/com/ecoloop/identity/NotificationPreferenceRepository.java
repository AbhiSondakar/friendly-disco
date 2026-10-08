package com.ecoloop.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    Optional<NotificationPreference> findByUserId(UUID userId);
    java.util.List<NotificationPreference> findAllByUserIdIn(java.util.Collection<UUID> userIds);
}
