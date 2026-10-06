package com.ecoloop.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PushTokenRepository pushTokens;
    private final NotificationPreferenceRepository notificationPrefs;
    private final com.ecoloop.rewards.RewardLedgerRepository ledger;
    private final SessionRevocationService sessionRevocationService;

    public UserController(UserRepository users, PasswordEncoder encoder, PushTokenRepository pushTokens,
                          NotificationPreferenceRepository notificationPrefs,
                          com.ecoloop.rewards.RewardLedgerRepository ledger,
                          SessionRevocationService sessionRevocationService) {
        this.users = users;
        this.encoder = encoder;
        this.pushTokens = pushTokens;
        this.notificationPrefs = notificationPrefs;
        this.ledger = ledger;
        this.sessionRevocationService = sessionRevocationService;
    }

    private User current(HttpServletRequest request) {
        UUID userId = com.ecoloop.common.SessionUser.require(request).id();
        return users.findById(userId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User not found"));
    }

    @GetMapping
    public Map<String, Object> me(HttpServletRequest request) {
        var u = current(request);
        return Map.of(
            "id", u.getId(),
            "email", u.getEmail(),
            "name", u.getName(),
            "phone", u.getPhone() != null ? u.getPhone() : "",
            "address", u.getAddress() != null ? u.getAddress() : "",
            "role", u.getRole(),
            "pointsBalance", ledger.balance(u.getId())
        );
    }

    public record ProfileUpdate(
        String name,
        String phone,
        String address) {}

    @PatchMapping
    public Map<String, Object> update(@RequestBody ProfileUpdate body, HttpServletRequest request) {
        var u = current(request);
        if (body.name() != null) u.setName(body.name());
        if (body.phone() != null) u.setPhone(body.phone());
        if (body.address() != null) u.setAddress(body.address());
        u.setUpdatedAt(Instant.now());
        users.save(u);
        return me(request);
    }

    public record PasswordChange(
        @NotBlank String currentPassword,
        @NotBlank String newPassword) {}

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChange body, HttpServletRequest request) {
        var u = current(request);
        if (!encoder.matches(body.currentPassword(), u.getPasswordHash())) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Current password is invalid");
        }
        PasswordPolicy.validate(body.newPassword());
        u.setPasswordHash(encoder.encode(body.newPassword()));
        u.setUpdatedAt(Instant.now());
        users.save(u);
        sessionRevocationService.revokeAllUserSessions(u.getEmail());
    }

    @PostMapping("/license")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveLicense(@RequestBody Map<String, String> body, HttpServletRequest request) {
        log.info("License uploaded for user {}", current(request).getId());
    }

    public record PushTokenRequest(
        @NotBlank String token,
        String platform) {}

    @GetMapping("/notification-prefs")
    public Map<String, Object> getNotificationPrefs(HttpServletRequest request) {
        UUID uid = current(request).getId();
        NotificationPreference pref = notificationPrefs.findByUserId(uid)
            .orElseGet(() -> new NotificationPreference(uid));
        return prefsMap(pref);
    }

    @PutMapping("/notification-prefs")
    public Map<String, Object> setNotificationPrefs(@RequestBody NotificationPrefs body, HttpServletRequest request) {
        UUID uid = current(request).getId();
        NotificationPreference pref = notificationPrefs.findByUserId(uid)
            .orElseGet(() -> new NotificationPreference(uid));
        pref.setPickupUpdates(body.pickupUpdates);
        pref.setPointsUpdates(body.pointsUpdates);
        pref.setOfferAlerts(body.offerAlerts);
        pref.setUpdatedAt(Instant.now());
        notificationPrefs.save(pref);
        log.info("Notification prefs updated: user={}", uid);
        return prefsMap(pref);
    }

    private static Map<String, Object> prefsMap(NotificationPreference p) {
        return Map.of(
            "pickupUpdates", p.getPickupUpdates(),
            "pointsUpdates", p.getPointsUpdates(),
            "offerAlerts", p.getOfferAlerts()
        );
    }

    public record NotificationPrefs(boolean pickupUpdates, boolean pointsUpdates, boolean offerAlerts) {}

    @PostMapping("/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void savePushToken(@Valid @RequestBody PushTokenRequest body, HttpServletRequest request) {
        UUID userId = current(request).getId();
        pushTokens.save(new PushToken(userId, body.token(),
            body.platform() != null ? body.platform() : "unknown"));
    }
}
