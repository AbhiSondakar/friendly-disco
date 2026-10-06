package com.ecoloop.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimiterServiceTest {

    @Test
    void removesExpiredBucketsAndRetainsActiveBuckets() {
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        RateLimiterService service = new RateLimiterService(redisProvider);

        service.checkLimit("test", "expired", 5, Duration.ZERO);
        service.checkLimit("test", "active", 1, Duration.ofDays(1));

        assertEquals(1, service.removeExpiredMemoryBuckets(System.currentTimeMillis()));
        assertThrows(RateLimitException.class, () ->
            service.checkLimit("test", "active", 1, Duration.ofDays(1)));
    }
}
