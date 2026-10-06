package com.ecoloop.identity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "push_tokens")
@IdClass(PushToken.PushTokenId.class)
public class PushToken {

    public static class PushTokenId implements Serializable {
        private UUID userId;
        private String token;

        public PushTokenId() {}
        public PushTokenId(UUID userId, String token) {
            this.userId = userId;
            this.token = token;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PushTokenId that)) return false;
            return Objects.equals(userId, that.userId) && Objects.equals(token, that.token);
        }
        @Override public int hashCode() { return Objects.hash(userId, token); }
    }

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(nullable = false, length = 512)
    private String token;

    @Column(length = 20)
    private String platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PushToken() {}

    public PushToken(UUID userId, String token, String platform) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
