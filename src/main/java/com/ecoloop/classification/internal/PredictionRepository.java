package com.ecoloop.classification.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PredictionRepository extends JpaRepository<Prediction, UUID> {
    List<Prediction> findAllByRequestIdOrderByCreatedAtDesc(UUID requestId);
    Optional<Prediction> findByIdAndRequestId(UUID id, UUID requestId);
    List<Prediction> findAllByCreatedAtAfter(Instant createdAt);
}
