package com.ecoloop.notification;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Deprecated
    @Transactional
    public Notification create(UUID userId, String type, String title, String body) {
        NotificationType nt = NotificationType.fromValue(type);
        NotificationTemplates.Rendered rendered = nt != null
            ? NotificationTemplates.render(nt, Map.of())
            : new NotificationTemplates.Rendered(title, body);
        return repository.save(new Notification(userId, nt, rendered.title(), rendered.body(), null));
    }

    @Transactional
    public Notification create(UUID recipientId,
                               NotificationType type,
                               UUID referenceId,
                               Map<String, Object> params) {
        if (recipientId == null) {
            throw new IllegalArgumentException("recipientId is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        NotificationTemplates.Rendered rendered = NotificationTemplates.render(type, params == null ? Map.of() : params);
        Notification candidate = new Notification(recipientId, type, rendered.title(), rendered.body(), referenceId);

        try {
            return repository.save(candidate);
        } catch (DataIntegrityViolationException duplicate) {
            return repository.findByUserIdAndTypeAndReferenceId(recipientId, type, referenceId)
                .orElseThrow(() -> duplicate);
        }
    }

    @Transactional
    public Notification markRead(UUID userId, UUID id) {
        var notification = repository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        notification.markRead();
        return repository.save(notification);
    }
}
