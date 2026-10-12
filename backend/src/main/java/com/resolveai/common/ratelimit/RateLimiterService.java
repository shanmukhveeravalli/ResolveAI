package com.resolveai.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise Rate Limiter Service supporting Redis distributed counting with
 * transparent in-memory fallback for local development, test environments, and Redis outages.
 */
@Slf4j
@Service
public class RateLimiterService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, InMemoryWindow> inMemoryCache = new ConcurrentHashMap<>();

    public RateLimiterService() {
        this.stringRedisTemplate = null;
    }

    @Autowired
    public RateLimiterService(@Autowired(required = false) StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Checks rate limit and throws RateLimitExceededException if exceeded.
     */
    public void checkLimit(String key, int maxRequests, int periodSeconds) {
        if (!tryAcquire(key, maxRequests, periodSeconds)) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded (" + maxRequests + " requests per " + periodSeconds + "s). Please try again later.",
                    periodSeconds
            );
        }
    }

    /**
     * Attempts to acquire a permit for the specified key within the time window.
     *
     * @param key           rate limit identifier (e.g. "auth_login:192.168.1.1")
     * @param maxRequests   maximum allowed requests in window
     * @param periodSeconds window duration in seconds
     * @return true if request is permitted, false if rate limit is exceeded
     */
    public boolean tryAcquire(String key, int maxRequests, int periodSeconds) {
        if (maxRequests <= 0 || periodSeconds <= 0) {
            return true;
        }

        if (stringRedisTemplate != null) {
            try {
                return tryAcquireRedis(key, maxRequests, periodSeconds);
            } catch (Exception e) {
                log.debug("Redis rate limiting failed, falling back to in-memory: {}", e.getMessage());
            }
        }

        return tryAcquireInMemory(key, maxRequests, periodSeconds);
    }

    private boolean tryAcquireRedis(String key, int maxRequests, int periodSeconds) {
        String redisKey = "resolveai:ratelimit:" + key;
        Long currentCount = stringRedisTemplate.opsForValue().increment(redisKey);

        if (currentCount != null && currentCount == 1L) {
            stringRedisTemplate.expire(redisKey, Duration.ofSeconds(periodSeconds));
        }

        return currentCount != null && currentCount <= maxRequests;
    }

    private boolean tryAcquireInMemory(String key, int maxRequests, int periodSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - (now % periodSeconds);

        // Periodically purge stale entries
        if (inMemoryCache.size() > 5000) {
            inMemoryCache.entrySet().removeIf(entry -> entry.getValue().windowStart < (now - periodSeconds * 2));
        }

        InMemoryWindow window = inMemoryCache.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart != windowStart) {
                return new InMemoryWindow(windowStart, new AtomicInteger(1));
            }
            existing.counter.incrementAndGet();
            return existing;
        });

        return window.counter.get() <= maxRequests;
    }

    private static class InMemoryWindow {
        final long windowStart;
        final AtomicInteger counter;

        InMemoryWindow(long windowStart, AtomicInteger counter) {
            this.windowStart = windowStart;
            this.counter = counter;
        }
    }
}
