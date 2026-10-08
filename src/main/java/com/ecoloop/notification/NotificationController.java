package com.ecoloop.notification;

import com.ecoloop.common.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@Validated
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
    public PageResponse<NotificationDto> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            HttpServletRequest r) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> notificationPage = notifications.findAllByUserId(user(r), pageable);
        return PageResponse.of(notificationPage).mapContent(NotificationDto::from);
    }

    /**
     * Unread count returns a scalar and is intentionally not paginated.
     */
    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(HttpServletRequest r) {
        return Map.of("count", notifications.countByUserIdAndReadFalse(user(r)));
    }

    @PatchMapping("/{id}/read")
    public NotificationDto read(@PathVariable UUID id, HttpServletRequest r) {
        return NotificationDto.from(service.markRead(user(r), id));
    }
}
