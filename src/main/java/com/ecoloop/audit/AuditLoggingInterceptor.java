package com.ecoloop.audit;

import com.ecoloop.common.SessionUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class AuditLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("ecoloop.audit");
    private static final int MAX_DETAIL_FIELDS = 20;
    private static final int MAX_DETAIL_VALUE_LENGTH = 256;
    private static final Set<String> SENSITIVE_FIELD_NAMES = AuditRedactionKeys.SENSITIVE_FIELD_NAMES;

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AuditLoggingInterceptor(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        request.setAttribute("ecoloop.audit.start", System.nanoTime());
        log.info("{} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        Long start = (Long) request.getAttribute("ecoloop.audit.start");
        long ms = start == null ? 0L : (System.nanoTime() - start) / 1_000_000;
        int status = response.getStatus();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        String actorIdStr = SessionUser.current().map(u -> u.id().toString()).orElse("anonymous");
        String role = SessionUser.current().map(SessionUser::role).orElse("NONE");

        log.info("{} {} status={} latency={}ms actor={} role={}",
                method, uri, status, ms, actorIdStr, role);

        if (ex != null) {
            log.warn("Request {} {} completed with exception: {}", method, uri, ex.getMessage());
        }

        if (uri.startsWith("/api/") && isMutating(method)) {
            recordAudit(request, status);
        }
    }

    private void recordAudit(HttpServletRequest request, int status) {
        try {
            UUID actorId = SessionUser.current().map(SessionUser::id).orElse(null);
            String role = SessionUser.current().map(SessionUser::role).orElse(null);

            String[] segments = request.getRequestURI().replaceFirst("^/api/", "").split("/", 0);
            String entityType = segments.length > 0 && !segments[0].isBlank() ? segments[0] : null;
            UUID entityId = extractId(segments);

            String action = request.getMethod() + " " + request.getRequestURI();
            String result = status >= 500 ? "server_error"
                    : (status >= 400 ? "client_error" : "success");

            auditService.record(actorId, role, action, entityType, entityId, result,
                requestDetails(request));
        } catch (Exception e) {
            log.warn("Audit recording failed for {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
        }
    }

    private static boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private static UUID extractId(String[] segments) {
        for (int index = segments.length - 1; index >= 0; index--) {
            try {
                return UUID.fromString(segments[index]);
            } catch (IllegalArgumentException ignored) {
                // Endpoint action suffixes, such as /{id}/role, are not entity identifiers.
            }
        }
        return null;
    }

    private Map<String, Object> requestDetails(HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", request.getMethod());
        details.put("path", request.getRequestURI());

        Map<String, Object> query = sanitizeParameters(request.getParameterMap());
        if (!query.isEmpty()) {
            details.put("parameters", query);
        }

        // Only administrative request bodies are audit metadata. Household and partner bodies
        // can contain address, contact, image, or evidence data and must not enter the audit log.
        if (request.getRequestURI().startsWith("/api/admin/")) {
            bodyDetails(request).ifPresent(body -> details.put("body", body));
        }
        return details;
    }

    private java.util.Optional<Object> bodyDetails(HttpServletRequest request) {
        if (request.getContentType() == null
            || !request.getContentType().toLowerCase(Locale.ROOT).startsWith("application/json")
            || !(request instanceof ContentCachingRequestWrapper cachedRequest)) {
            return java.util.Optional.empty();
        }

        byte[] body = cachedRequest.getContentAsByteArray();
        if (body.length == 0) {
            return java.util.Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            Object sanitized = sanitizeJson(root);
            if (request.getContentLengthLong() > AuditRequestDetailsFilter.MAX_AUDIT_BODY_BYTES) {
                Map<String, Object> truncated = new LinkedHashMap<>();
                truncated.put("captured", sanitized);
                truncated.put("truncated", true);
                return java.util.Optional.of(truncated);
            }
            return java.util.Optional.of(sanitized);
        } catch (Exception ignored) {
            return java.util.Optional.of("[unavailable]");
        }
    }

    private Map<String, Object> sanitizeParameters(Map<String, String[]> parameters) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            if (sanitized.size() >= MAX_DETAIL_FIELDS) {
                break;
            }
            String name = entry.getKey();
            if (isSensitive(name)) {
                sanitized.put(name, "[redacted]");
            } else {
                List<String> values = new ArrayList<>();
                for (String value : entry.getValue()) {
                    if (values.size() >= MAX_DETAIL_FIELDS) {
                        break;
                    }
                    values.add(truncate(value));
                }
                sanitized.put(name, values.size() == 1 ? values.getFirst() : values);
            }
        }
        return sanitized;
    }

    private Object sanitizeJson(JsonNode value) {
        if (value.isObject()) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext() && sanitized.size() < MAX_DETAIL_FIELDS) {
                Map.Entry<String, JsonNode> field = fields.next();
                sanitized.put(field.getKey(), isSensitive(field.getKey())
                    ? "[redacted]" : sanitizeJson(field.getValue()));
            }
            return sanitized;
        }
        if (value.isArray()) {
            List<Object> sanitized = new ArrayList<>();
            for (JsonNode item : value) {
                if (sanitized.size() >= MAX_DETAIL_FIELDS) {
                    break;
                }
                sanitized.add(sanitizeJson(item));
            }
            return sanitized;
        }
        if (value.isTextual()) {
            return truncate(value.textValue());
        }
        if (value.isNumber()) {
            return value.numberValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return "[unavailable]";
    }

    private static boolean isSensitive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_NAMES.stream().anyMatch(lower::contains);
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_DETAIL_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_DETAIL_VALUE_LENGTH) + "…";
    }
}
