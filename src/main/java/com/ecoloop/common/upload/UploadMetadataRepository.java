package com.ecoloop.common.upload;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadMetadataRepository extends JpaRepository<UploadMetadata, UUID> {
    List<UploadMetadata> findAllByUserId(UUID userId);
    List<UploadMetadata> findAllByUserIdAndPurpose(UUID userId, String purpose);
    Optional<UploadMetadata> findByIdAndUserId(UUID id, UUID userId);
}
