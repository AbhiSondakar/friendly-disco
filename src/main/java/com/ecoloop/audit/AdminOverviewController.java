package com.ecoloop.audit;

import com.ecoloop.classification.internal.PredictionRepository;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.pickup.PickupRepository;
import com.ecoloop.pickup.PickupRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/overview")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOverviewController {

    private final UserRepository users;
    private final PartnerRepository partners;
    private final PickupRepository pickups;
    private final PredictionRepository predictions;
    private final AuditLogRepository audit;

    private static final DateTimeFormatter DAY_KEY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd").withZone(ZoneOffset.UTC);

    public AdminOverviewController(UserRepository users,
                                   PartnerRepository partners,
                                   PickupRepository pickups,
                                   PredictionRepository predictions,
                                   AuditLogRepository audit) {
        this.users = users;
        this.partners = partners;
        this.pickups = pickups;
        this.predictions = predictions;
        this.audit = audit;
    }

    public record DailyPickupCount(String date, long count) {}

    public record RecentActivityItem(String id, String action, String entity, String actor, String timestamp, String result) {}

    public record OverviewResponse(
        long totalUsers,
        long totalPartners,
        long totalPickups,
        long pendingPickups,
        long totalPredictions,
        List<DailyPickupCount> pickupsLast7d,
        List<RecentActivityItem> recentActivity
    ) {}

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public OverviewResponse overview() {
        long totalUsers = users.count();
        long totalPartners = partners.count();
        long totalPickups = pickups.count();
        long pendingPickups = pickups.countByStatus("pending");
        long totalPredictions = predictions.count();

        List<DailyPickupCount> pickupsLast7d = computePickupsLast7d();
        List<RecentActivityItem> recentActivity = fetchRecentActivity();

        return new OverviewResponse(
            totalUsers,
            totalPartners,
            totalPickups,
            pendingPickups,
            totalPredictions,
            pickupsLast7d,
            recentActivity
        );
    }

    private List<DailyPickupCount> computePickupsLast7d() {
        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(6));
        List<PickupRequest> recentPickups = pickups.findAllByCreatedAtAfter(sevenDaysAgo);

        Map<String, Long> countsByDay = new LinkedHashMap<>();
        Instant now = Instant.now();
        for (int i = 6; i >= 0; i--) {
            Instant dayInstant = now.minus(Duration.ofDays(i));
            String key = DAY_KEY_FORMATTER.format(dayInstant);
            countsByDay.put(key, 0L);
        }

        for (PickupRequest p : recentPickups) {
            Instant ts = p.getCreatedAt() != null ? p.getCreatedAt() : (p.getUpdatedAt() != null ? p.getUpdatedAt() : Instant.EPOCH);
            String key = DAY_KEY_FORMATTER.format(ts);
            countsByDay.merge(key, 1L, Long::sum);
        }

        List<DailyPickupCount> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : countsByDay.entrySet()) {
            result.add(new DailyPickupCount(e.getKey(), e.getValue()));
        }
        return result;
    }

    private List<RecentActivityItem> fetchRecentActivity() {
        List<AuditLog> logs = audit.findAll(
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        List<RecentActivityItem> items = new ArrayList<>();
        for (int i = 0; i < logs.size(); i++) {
            AuditLog a = logs.get(i);
            UUID entityId = a.getEntityId();
            String entity = "-";
            if (a.getEntityType() != null) {
                String shortId = entityId != null ? entityId.toString().substring(0, 8) : "";
                entity = a.getEntityType() + ":" + shortId;
            }
            items.add(new RecentActivityItem(
                a.getId() != null ? a.getId().toString() : String.valueOf(i),
                a.getAction() != null ? a.getAction() : "unknown",
                entity,
                a.getActorRole() != null ? a.getActorRole() : "system",
                a.getCreatedAt() != null ? a.getCreatedAt().toString() : "",
                a.getResult() != null ? a.getResult() : "-"
            ));
        }
        return items;
    }
}
