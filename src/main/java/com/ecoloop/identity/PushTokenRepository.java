package com.ecoloop.identity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PushTokenRepository extends JpaRepository<PushToken, PushToken.PushTokenId> {
}
