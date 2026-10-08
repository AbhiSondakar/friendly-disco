package com.ecoloop.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
    UUID id,
    UUID userId,
    String title,
    String body,
    boolean read,
    String type,
    UUID referenceId,
    Instant createdAt
) {
    public static NotificationDto from(Notification n) {
        if (n == null) return null;
        return new NotificationDto(
            n.getId(),
            n.getUserId(),
            n.getTitle(),
            n.getBody(),
            n.isRead(),
            n.getType(),
            n.getReferenceId(),
            n.getCreatedAt()
        );
    }
}
