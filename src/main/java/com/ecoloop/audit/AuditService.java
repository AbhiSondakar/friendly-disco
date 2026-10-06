package com.ecoloop.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(UUID actorId, String actorRole, String action, String entityType, UUID entityId, String result) {
        record(actorId, actorRole, action, entityType, entityId, result, Map.of());
    }

    @Transactional
    public void record(UUID actorId, String actorRole, String action, String entityType, UUID entityId,
                       String result, Map<String, Object> details) {
        repository.save(new AuditLog(actorId, actorRole, action, entityType, entityId, result,
            details == null ? Map.of() : Map.copyOf(details)));
    }
}
