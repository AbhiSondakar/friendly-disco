package com.ecoloop.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
        "local current = redis.call('INCR', KEYS[1]) "
            + "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
            + "return current",
        Long.class
    );

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ConcurrentHashMap<String, MemoryCounter> memoryBuckets = new ConcurrentHashMap<>();

    public RateLimiterService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public void checkLimit(String prefix, String identifier, int maxAttempts, Duration window) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }

        String key = "ratelimit:" + prefix + ":" + identifier;
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();

        if (redis != null) {
            try {
                Long current = redis.execute(INCREMENT_WITH_TTL, List.of(key),
                    Long.toString(window.toMillis()));

                if (current != null && current > maxAttempts) {
                    Long expireSec = redis.getExpire(key, TimeUnit.SECONDS);
                    long retryAfter = (expireSec != null && expireSec > 0) ? expireSec : window.toSeconds();
                    log.warn("Rate limit exceeded for prefix={}, retryAfter={}s", prefix, retryAfter);
                    throw new RateLimitException(retryAfter, "Too many requests. Please try again later.");
                }
                return;
            } catch (RateLimitException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Redis rate-limiting failed, falling back to in-memory: {}", e.getMessage());
            }
        }

        // In-memory fallback
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        MemoryCounter counter = memoryBuckets.compute(key, (k, existing) -> {
            if (existing == null || now > existing.expiresAt) {
                return new MemoryCounter(1, now + windowMillis);
            }
            existing.count++;
            return existing;
        });

        if (counter.count > maxAttempts) {
            long remainingSec = Math.max(1, (counter.expiresAt - now) / 1000);
            log.warn("In-memory rate limit exceeded for prefix={}, retryAfter={}s", prefix, remainingSec);
            throw new RateLimitException(remainingSec, "Too many requests. Please try again later.");
        }
    }

    private static final int DEFAULT_MAX_LOGIN_FAILURES = 5;
    private static final Duration DEFAULT_LOCKOUT_WINDOW = Duration.ofMinutes(15);

    public void checkLoginLockout(String accountEmail) {
        if (accountEmail == null || accountEmail.isBlank()) {
            return;
        }
        String key = "lockout:account:" + accountEmail.toLowerCase().trim();
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            try {
                String val = redis.opsForValue().get(key);
                if (val != null) {
                    long failures = Long.parseLong(val);
                    if (failures >= DEFAULT_MAX_LOGIN_FAILURES) {
                        Long expireSec = redis.getExpire(key, TimeUnit.SECONDS);
                        long retryAfter = (expireSec != null && expireSec > 0) ? expireSec : DEFAULT_LOCKOUT_WINDOW.toSeconds();
                        log.warn("Account is locked out: email={}, retryAfter={}s", accountEmail, retryAfter);
                        throw new RateLimitException(retryAfter, "Account temporarily locked due to consecutive failed login attempts. Please try again later.");
                    }
                }
                return;
            } catch (RateLimitException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Redis lockout check failed, falling back to memory: {}", e.getMessage());
            }
        }

        MemoryCounter counter = memoryBuckets.get(key);
        if (counter != null && counter.count >= DEFAULT_MAX_LOGIN_FAILURES) {
            long now = System.currentTimeMillis();
            if (now < counter.expiresAt) {
                long remainingSec = Math.max(1, (counter.expiresAt - now) / 1000);
                throw new RateLimitException(remainingSec, "Account temporarily locked due to consecutive failed login attempts. Please try again later.");
            } else {
                memoryBuckets.remove(key, counter);
            }
        }
    }

    public void recordLoginFailure(String accountEmail) {
        if (accountEmail == null || accountEmail.isBlank()) {
            return;
        }
        String key = "lockout:account:" + accountEmail.toLowerCase().trim();
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            try {
                Long current = redis.execute(INCREMENT_WITH_TTL, List.of(key),
                    Long.toString(DEFAULT_LOCKOUT_WINDOW.toMillis()));
                log.info("Recorded login failure for account: {}, count: {}", accountEmail, current);
                return;
            } catch (Exception e) {
                log.warn("Redis recordLoginFailure failed, falling back to memory: {}", e.getMessage());
            }
        }

        long now = System.currentTimeMillis();
        long windowMillis = DEFAULT_LOCKOUT_WINDOW.toMillis();
        memoryBuckets.compute(key, (k, existing) -> {
            if (existing == null || now > existing.expiresAt) {
                return new MemoryCounter(1, now + windowMillis);
            }
            existing.count++;
            return existing;
        });
    }

    public void resetLoginFailures(String accountEmail) {
        if (accountEmail == null || accountEmail.isBlank()) {
            return;
        }
        String key = "lockout:account:" + accountEmail.toLowerCase().trim();
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.delete(key);
            } catch (Exception e) {
                log.warn("Redis resetLoginFailures failed: {}", e.getMessage());
            }
        }
        memoryBuckets.remove(key);
    }

    @Scheduled(fixedDelay = 60_000)
    public void purgeExpiredMemoryBuckets() {
        removeExpiredMemoryBuckets(System.currentTimeMillis());
    }

    int removeExpiredMemoryBuckets(long now) {
        int removed = 0;
        for (var entry : memoryBuckets.entrySet()) {
            MemoryCounter counter = entry.getValue();
            if (counter.expiresAt <= now && memoryBuckets.remove(entry.getKey(), counter)) {
                removed++;
            }
        }
        return removed;
    }

    private static class MemoryCounter {
        int count;
        final long expiresAt;

        MemoryCounter(int count, long expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }
    }
}
