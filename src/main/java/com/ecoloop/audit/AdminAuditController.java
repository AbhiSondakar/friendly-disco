package com.ecoloop.audit;

import com.ecoloop.classification.internal.Prediction;
import com.ecoloop.classification.internal.PredictionRepository;
import com.ecoloop.common.web.PageResponse;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.pickup.PickupRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditLogRepository audit;
    private final UserRepository users;
    private final PartnerRepository partners;
    private final PickupRepository pickups;
    private final PredictionRepository predictions;
    private final Optional<BuildProperties> buildProperties;
    private final DataSource dataSource;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;

    public AdminAuditController(AuditLogRepository audit,
                                UserRepository users,
                                PartnerRepository partners,
                                PickupRepository pickups,
                                PredictionRepository predictions,
                                Optional<BuildProperties> buildProperties,
                                DataSource dataSource,
                                ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider) {
        this.audit = audit;
        this.users = users;
        this.partners = partners;
        this.pickups = pickups;
        this.predictions = predictions;
        this.buildProperties = buildProperties;
        this.dataSource = dataSource;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
    }

    @GetMapping("/api/admin/audit")
    public PageResponse<AuditLog> listAudit(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "100") int size,
                                            @RequestParam(required = false) String search) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.min(Math.max(1, size), 500);
        Specification<AuditLog> spec = AuditSpecifications.withSearch(search);
        return PageResponse.of(audit.findAll(spec, PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping(value = "/api/admin/audit/export", produces = "text/csv")
    public ResponseEntity<String> exportAuditCsv(@RequestParam(defaultValue = "1000") int limit,
                                                 @RequestParam(required = false) String search) {
        int boundedLimit = Math.min(Math.max(1, limit), 5000);
        Specification<AuditLog> spec = AuditSpecifications.withSearch(search);
        List<AuditLog> logs = audit.findAll(spec, PageRequest.of(0, boundedLimit, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();

        StringBuilder sb = new StringBuilder();
        sb.append("id,actor_id,actor_role,action,entity_type,entity_id,result,created_at\n");
        for (AuditLog l : logs) {
            sb.append(escapeCsv(l.getId() != null ? l.getId().toString() : "")).append(',');
            sb.append(escapeCsv(l.getActorId() != null ? l.getActorId().toString() : "")).append(',');
            sb.append(escapeCsv(l.getActorRole() != null ? l.getActorRole() : "")).append(',');
            sb.append(escapeCsv(l.getAction() != null ? l.getAction() : "")).append(',');
            sb.append(escapeCsv(l.getEntityType() != null ? l.getEntityType() : "")).append(',');
            sb.append(escapeCsv(l.getEntityId() != null ? l.getEntityId().toString() : "")).append(',');
            sb.append(escapeCsv(l.getResult() != null ? l.getResult() : "")).append(',');
            sb.append(escapeCsv(l.getCreatedAt() != null ? l.getCreatedAt().toString() : "")).append('\n');
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "audit-log.csv");
        return new ResponseEntity<>(sb.toString(), headers, HttpStatus.OK);
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    @GetMapping("/api/admin/health")
    public Map<String, Object> health() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration uptime = Duration.ofMillis(uptimeMs);
        String uptimeStr = String.format("%dd %dh %dm %ds",
            uptime.toDaysPart(), uptime.toHoursPart(),
            uptime.toMinutesPart(), uptime.toSecondsPart());

        long totalUsers = users.count();
        long totalPartners = partners.count();
        long totalPickups = pickups.count();
        long pendingPickups = pickups.countByStatus("pending");
        long totalPredictions = predictions.count();

        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
        List<Prediction> recentList = predictions.findAllByCreatedAtAfter(oneHourAgo);
        long recentPredictions = recentList.size();

        int latencyP50 = 0;
        if (!recentList.isEmpty()) {
            List<Integer> sortedLatencies = recentList.stream()
                .map(Prediction::getLatencyMs)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
            if (!sortedLatencies.isEmpty()) {
                latencyP50 = sortedLatencies.get(sortedLatencies.size() / 2);
            }
        }

        Map<String, Object> services = new LinkedHashMap<>();

        // Real Database Check
        Map<String, Object> db = new LinkedHashMap<>();
        long dbStart = System.currentTimeMillis();
        boolean dbHealthy = false;
        try (Connection conn = dataSource.getConnection()) {
            dbHealthy = conn.isValid(2);
            long dbLatency = System.currentTimeMillis() - dbStart;
            db.put("status", dbHealthy ? "UP" : "DOWN");
            db.put("latency_ms", dbLatency);
            db.put("error_rate", 0.0);
            db.put("uptime", uptimeStr);
        } catch (Exception e) {
            db.put("status", "DOWN");
            db.put("error", e.getMessage());
            db.put("latency_ms", System.currentTimeMillis() - dbStart);
        }
        services.put("database", db);

        // Real Redis / Session Check
        Map<String, Object> sess = new LinkedHashMap<>();
        sess.put("provider", "redis");
        sess.put("namespace", "ecoloop:session");
        sess.put("timeout", "7d");
        RedisConnectionFactory redisFactory = redisConnectionFactoryProvider.getIfAvailable();
        if (redisFactory != null) {
            long redisStart = System.currentTimeMillis();
            try (RedisConnection conn = redisFactory.getConnection()) {
                String ping = conn.ping();
                long redisLatency = System.currentTimeMillis() - redisStart;
                boolean redisUp = "PONG".equalsIgnoreCase(ping);
                sess.put("status", redisUp ? "UP" : "DOWN");
                sess.put("latency_ms", redisLatency);
            } catch (Exception e) {
                sess.put("status", "DOWN");
                sess.put("error", e.getMessage());
            }
        } else {
            sess.put("status", "DISABLED");
            sess.put("provider", "in-memory");
        }
        services.put("session", sess);

        // Classification Service Check
        Map<String, Object> classification = new LinkedHashMap<>();
        classification.put("status", "UP");
        classification.put("total_predictions", totalPredictions);
        classification.put("predictions_last_hour", recentPredictions);
        classification.put("latency_ms_p50", latencyP50);
        services.put("classification", classification);

        // Overall Application Info
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", "ecoloop");
        app.put("version", buildProperties.map(BuildProperties::getVersion).orElse("dev"));
        app.put("uptime_ms", uptimeMs);
        app.put("uptime", uptimeStr);
        app.put("started_at", Instant.now().minusMillis(uptimeMs).toString());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_users", totalUsers);
        metrics.put("total_partners", totalPartners);
        metrics.put("total_pickups", totalPickups);
        metrics.put("pending_pickups", pendingPickups);
        metrics.put("total_predictions", totalPredictions);

        String overallStatus = dbHealthy ? "UP" : "DEGRADED";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", overallStatus);
        result.put("application", app);
        result.put("services", services);
        result.put("metrics", metrics);
        return result;
    }
}
