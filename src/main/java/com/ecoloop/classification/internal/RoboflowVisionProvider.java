package com.ecoloop.classification.internal;

import com.ecoloop.classification.api.ClassificationResult;
import com.ecoloop.classification.api.VisionProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RoboflowVisionProvider implements VisionProvider {

    private static final Logger log = LoggerFactory.getLogger(RoboflowVisionProvider.class);
    private static final Set<String> CANONICAL = Set.of("laptop", "phone", "tablet", "battery", "appliance", "other");
    private static final double CONFIDENCE_FLOOR = 0.2;

    private final RestClient restClient;
    private final String apiKey;
    private final String workflow;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntilMillis = new AtomicLong(0);

    @Value("${ai.roboflow.max-attempts:2}")
    private int maxAttempts = 2;

    @Value("${ai.roboflow.retry-backoff-ms:200}")
    private long retryBackoffMillis = 200;

    @Value("${ai.roboflow.circuit-breaker.failure-threshold:5}")
    private int circuitFailureThreshold = 5;

    @Value("${ai.roboflow.circuit-breaker.cooldown-ms:30000}")
    private long circuitCooldownMillis = 30_000;

    public RoboflowVisionProvider(@Qualifier("roboflowRestClient") RestClient restClient,
                                  @Value("${ai.roboflow.api-key:}") String apiKey,
                                  @Value("${ai.roboflow.workflow:}") String workflow,
                                  ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.workflow = workflow;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public String name() {
        return "roboflow";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && workflow != null && !workflow.isBlank();
    }

    @Override
    public ClassificationResult classify(byte[] image, String mime) {
        recordMetric("ecoloop.ai.classification.attempts");
        if (isCircuitOpen()) {
            recordMetric("ecoloop.ai.classification.circuit_open");
            return failedResult();
        }

        long start = System.currentTimeMillis();

        String base64Image = Base64.getEncoder().encodeToString(image);
        Map<String, Object> requestBody = Map.of(
            "inputs", Map.of(
                "image", Map.of(
                    "type", "base64",
                    "value", base64Image
                )
            )
        );

        String path = "/" + workflow;

        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                ResponseEntity<Map> entity = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toEntity(Map.class);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = entity.getBody();

                if (response == null) {
                    log.warn("Roboflow returned a null response for the configured workflow");
                    recordFailure();
                    recordMetric("ecoloop.ai.classification.failures");
                    return failedResult();
                }

                Prediction top = topPrediction(response);
                if (top == null || top.clazz == null) {
                    log.warn("Roboflow returned no usable predictions for the configured workflow");
                    recordSuccess();
                    recordMetric("ecoloop.ai.classification.empty");
                    return new ClassificationResult("other", 0.0, "roboflow", workflow, "manual_review");
                }

                String category = mapLabel(top.clazz);
                double confidence = top.confidence;
                long duration = System.currentTimeMillis() - start;

                log.info("Roboflow classification completed: class={} category={} confidence={} duration={}ms",
                        top.clazz, category, confidence, duration);

                recordSuccess();
                if ("other".equals(category) || confidence < CONFIDENCE_FLOOR) {
                    recordMetric("ecoloop.ai.classification.low_confidence");
                    return new ClassificationResult("other", confidence, "roboflow", workflow, "manual_review");
                }

                recordMetric("ecoloop.ai.classification.success");
                return new ClassificationResult(category, confidence, "roboflow", workflow, "completed");
            } catch (Exception e) {
                if (isRetryable(e) && attempt < attempts) {
                    log.warn("Roboflow classification attempt {} of {} failed: exception={}",
                        attempt, attempts, e.getClass().getSimpleName());
                    if (!waitBeforeRetry(attempt)) {
                        break;
                    }
                    continue;
                }

                log.warn("Roboflow classification failed after {} attempt(s): exception={}",
                    attempt, e.getClass().getSimpleName());
                recordFailure();
                recordMetric("ecoloop.ai.classification.failures");
                return failedResult();
            }
        }

        recordFailure();
        recordMetric("ecoloop.ai.classification.failures");
        return failedResult();
    }

    private ClassificationResult failedResult() {
        return new ClassificationResult("other", 0.0, "roboflow", workflow, "failed");
    }

    private boolean isRetryable(Exception exception) {
        return exception instanceof ResourceAccessException || exception instanceof HttpServerErrorException;
    }

    private boolean waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(Math.max(0, retryBackoffMillis) * attempt);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isCircuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntilMillis.get();
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= Math.max(1, circuitFailureThreshold)) {
            circuitOpenUntilMillis.accumulateAndGet(
                System.currentTimeMillis() + Math.max(1, circuitCooldownMillis), Math::max);
            consecutiveFailures.set(0);
            log.warn("Roboflow classification circuit opened after consecutive failures={}", failures);
        }
    }

    private void recordMetric(String metricName) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            try {
                registry.counter(metricName, "provider", "roboflow").increment();
            } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private static Prediction topPrediction(Map<String, Object> response) {
        if (response == null) return null;

        List<Map<String, Object>> candidates = null;

        // Structure 1: outputs[0].predictions
        if (response.get("outputs") instanceof List<?> outputs && !outputs.isEmpty()) {
            Object first = outputs.get(0);
            if (first instanceof Map<?, ?> outMap && outMap.get("predictions") instanceof List<?> preds) {
                candidates = (List<Map<String, Object>>) preds;
            }
        }

        // Structure 2: top-level predictions
        if ((candidates == null || candidates.isEmpty()) && response.get("predictions") instanceof List<?> preds) {
            candidates = (List<Map<String, Object>>) preds;
        }

        if (candidates == null || candidates.isEmpty()) return null;

        Prediction best = null;
        for (Map<String, Object> p : candidates) {
            Object cls = p.getOrDefault("class", p.getOrDefault("label", null));
            Number conf = (Number) p.get("confidence");
            if (cls == null || conf == null) continue;
            double c = conf.doubleValue();
            if (best == null || c > best.confidence) {
                best = new Prediction(cls.toString(), c);
            }
        }
        return best;
    }

    private static String mapLabel(String label) {
        if (label == null) return "other";
        String l = label.trim().toLowerCase(Locale.ROOT);
        if (l.contains("laptop") || l.contains("notebook") || l.contains("macbook") || l.contains("computer") || l.contains("pc")) return "laptop";
        if (l.contains("phone") || l.contains("smartphone") || l.contains("cellphone") || l.contains("mobile") || l.contains("iphone")) return "phone";
        if (l.contains("tablet") || l.contains("ipad") || l.contains("pad")) return "tablet";
        if (l.contains("battery") || l.contains("power bank")) return "battery";
        if (l.contains("appliance") || l.contains("fridge") || l.contains("refrigerator")
                || l.contains("washer") || l.contains("tv") || l.contains("television")
                || l.contains("monitor") || l.contains("keyboard") || l.contains("mouse")
                || l.contains("camera") || l.contains("headphone") || l.contains("charger")) return "appliance";
        if (CANONICAL.contains(l)) return l;
        if (l.endsWith("s")) {
            String singular = l.substring(0, l.length() - 1);
            if (CANONICAL.contains(singular)) return singular;
        }
        return "other";
    }

    private record Prediction(String clazz, double confidence) {}
}
