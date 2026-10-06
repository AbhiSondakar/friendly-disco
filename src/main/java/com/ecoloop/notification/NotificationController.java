package com.ecoloop.notification;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepository notifications;
    private final NotificationService service;

    public NotificationController(NotificationRepository n, NotificationService service) {
        this.notifications = n;
        this.service = service;
    }

    private UUID user(HttpServletRequest r) {
        return com.ecoloop.common.SessionUser.require(r).id();
    }

    @GetMapping
    public List<Notification> list(HttpServletRequest r) {
        return notifications.findAllByUserIdOrderByCreatedAtDesc(user(r));
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(HttpServletRequest r) {
        return Map.of("count", notifications.countByUserIdAndReadFalse(user(r)));
    }

    @PatchMapping("/{id}/read")
    public Notification read(@PathVariable UUID id, HttpServletRequest r) {
        return service.markRead(user(r), id);
    }
}
