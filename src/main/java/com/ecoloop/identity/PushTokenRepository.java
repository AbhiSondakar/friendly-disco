package com.ecoloop.identity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PushTokenRepository extends JpaRepository<PushToken, PushToken.PushTokenId> {
    void deleteAllByUserId(java.util.UUID userId);
    java.util.List<PushToken> findAllByUserId(java.util.UUID userId);
}
