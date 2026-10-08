package com.ecoloop.audit;

import com.ecoloop.classification.internal.Prediction;
import com.ecoloop.classification.internal.PredictionRepository;
import com.ecoloop.common.web.PageResponse;
import com.ecoloop.identity.UserRepository;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.pickup.PickupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.domain.Page;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminAuditController {

    private final AuditLogRepository audit;
    private final UserRepository users;
    private final PartnerRepository partners;
    private final PickupRepository pickups;
    private final PredictionRepository predictions;
    private final Optional<BuildProperties> buildProperties;
    private final DataSource dataSource;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminAuditController(AuditLogRepository audit,
                                UserRepository users,
                                PartnerRepository partners,
                                PickupRepository pickups,
                                PredictionRepository predictions,
                                Optional<BuildProperties> buildProperties,
                                DataSource dataSource,
                                ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
                                PlatformTransactionManager transactionManager) {
        this.audit = audit;
        this.users = users;
        this.partners = partners;
        this.pickups = pickups;
        this.predictions = predictions;
        this.buildProperties = buildProperties;
        this.dataSource = dataSource;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/audit")
    public PageResponse<AuditLogDto> listAudit(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String search) {
        Specification<AuditLog> spec = AuditSpecifications.withSearch(search);
        Page<AuditLog> auditPage = audit.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(auditPage).mapContent(AuditLogDto::from);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/api/admin/audit/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportAuditCsv(
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(required = false) String search) {
        // 1. Capture authorization data before returning the response
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized");
        }

        int boundedLimit = Math.min(Math.max(1, limit), 5000);
        Specification<AuditLog> spec = AuditSpecifications.withSearch(search);

        // 2. Stream response via worker thread using StreamingResponseBody
        StreamingResponseBody responseBody = outputStream -> {
            try (var writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                writer.write("id,actor_id,actor_role,action,entity_type,entity_id,result,details,created_at\n");
                int pageSize = 100;
                int fetched = 0;
                int pageNum = 0;
                while (fetched < boundedLimit) {
                    final int currentPage = pageNum++;
                    final int toFetch = Math.min(pageSize, boundedLimit - fetched);

                    // Execute each page fetch in REQUIRES_NEW transaction without SecurityContextHolder reliance
                    List<AuditLog> batch = transactionTemplate.execute(status -> {
                        return audit.findAll(spec, PageRequest.of(currentPage, toFetch, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
                    });

                    if (batch == null || batch.isEmpty()) {
                        break;
                    }

                    for (AuditLog l : batch) {
                        writer.write(escapeCsv(l.getId() != null ? l.getId().toString() : ""));
                        writer.write(',');
                        writer.write(escapeCsv(l.getActorId() != null ? l.getActorId().toString() : ""));
                        writer.write(',');
                        writer.write(escapeCsv(l.getActorRole() != null ? l.getActorRole() : ""));
                        writer.write(',');
                        writer.write(escapeCsv(l.getAction() != null ? l.getAction() : ""));
                        writer.write(',');
                        writer.write(escapeCsv(l.getEntityType() != null ? l.getEntityType() : ""));
                        writer.write(',');
                        writer.write(escapeCsv(l.getEntityId() != null ? l.getEntityId().toString() : ""));
                        writer.write(',');
                        writer.write(escapeCsv(l.getResult() != null ? l.getResult() : ""));
                        writer.write(',');
                        writer.write(escapeCsv(serializeAndRedactDetails(l.getDetails())));
                        writer.write(',');
                        writer.write(escapeCsv(l.getCreatedAt() != null ? l.getCreatedAt().toString() : ""));
                        writer.write('\n');
                        fetched++;
                    }
                    writer.flush();
                    if (batch.size() < toFetch) {
                        break;
                    }
                }
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "audit-log.csv");
        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    public static String escapeCsv(String s) {
        if (s == null || s.isEmpty()) return "";
        // Formula injection protection: if value starts with =, +, -, @, \t, \r, prepend single quote '
        char first = s.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String serializeAndRedactDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) return "{}";
        try {
            Map<String, Object> redacted = redactMap(details);
            return objectMapper.writeValueAsString(redacted);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> redactMap(Map<String, Object> map) {
        if (map == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (AuditRedactionKeys.isSensitive(key)) {
                result.put(key, "[redacted]");
            } else if (val instanceof Map<?, ?> nested) {
                result.put(key, redactMap((Map<String, Object>) nested));
            } else {
                result.put(key, val);
            }
        }
        return result;
    }

    @PreAuthorize("hasRole('ADMIN')")
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
