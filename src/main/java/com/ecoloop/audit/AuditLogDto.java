package com.ecoloop.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
    UUID id,
    UUID actorId,
    String actorRole,
    String action,
    String entityType,
    UUID entityId,
    String result,
    JsonNode details,
    Instant createdAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AuditLogDto from(AuditLog log) {
        if (log == null) return null;
        JsonNode detailsNode;
        try {
            detailsNode = log.getDetails() != null ? MAPPER.valueToTree(log.getDetails()) : NullNode.getInstance();
        } catch (Exception e) {
            detailsNode = NullNode.getInstance();
        }
        return new AuditLogDto(
            log.getId(),
            log.getActorId(),
            log.getActorRole(),
            log.getAction(),
            log.getEntityType(),
            log.getEntityId(),
            log.getResult(),
            detailsNode,
            log.getCreatedAt()
        );
    }
}
