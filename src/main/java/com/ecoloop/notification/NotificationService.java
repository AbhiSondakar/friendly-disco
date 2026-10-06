package com.ecoloop.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationService {
  private final NotificationRepository repository;
  public NotificationService(NotificationRepository repository) { this.repository = repository; }
  @Transactional public Notification create(UUID userId, String type, String title, String body) { return repository.save(new Notification(userId, type, title, body)); }
  @Transactional public Notification markRead(UUID userId, UUID id) {
    var notification = repository.findByIdAndUserId(id, userId).orElseThrow(() -> new IllegalArgumentException("Notification not found"));
    notification.markRead();
    return repository.save(notification);
  }
}
