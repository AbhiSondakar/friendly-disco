package com.ecoloop.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(length = 50)
    private String type;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {}

    public Notification(UUID userId, String type, String title, String body) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public void markRead() {
        this.read = true;
    }
}
