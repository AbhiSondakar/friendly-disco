package com.ecoloop.device;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    List<Device> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Device> findByIdAndUserId(UUID id, UUID userId);
}
