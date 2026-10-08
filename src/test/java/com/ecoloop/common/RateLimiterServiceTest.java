package com.ecoloop.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimiterServiceTest {

    @Test
    void removesExpiredBucketsAndRetainsActiveBuckets() {
        // A real empty provider avoids Mockito's inline-agent startup and exercises the
        // same no-Redis fallback used by the application when Redis is unavailable.
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        RateLimiterService service = new RateLimiterService(
            beans.getBeanProvider(StringRedisTemplate.class));

        service.checkLimit("test", "expired", 5, Duration.ZERO);
        service.checkLimit("test", "active", 1, Duration.ofDays(1));

        assertEquals(1, service.removeExpiredMemoryBuckets(System.currentTimeMillis()));
        assertThrows(RateLimitException.class, () ->
            service.checkLimit("test", "active", 1, Duration.ofDays(1)));
    }
}
