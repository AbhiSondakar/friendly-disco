package com.ecoloop.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(length = 50)
    private String result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {}

    public AuditLog(UUID actorId, String actorRole, String action, String entityType, UUID entityId, String result) {
        this(actorId, actorRole, action, entityType, entityId, result, null);
    }

    public AuditLog(UUID actorId, String actorRole, String action, String entityType, UUID entityId,
                    String result, Map<String, Object> details) {
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.result = result;
        this.details = details;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
