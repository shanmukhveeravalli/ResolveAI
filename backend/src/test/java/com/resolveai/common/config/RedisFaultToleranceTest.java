package com.resolveai.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Redis Fault-Tolerance & Resilience Tests (Phase 13)")
class RedisFaultToleranceTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    @Test
    @DisplayName("When Redis connection fails during cache GET, error handler absorbs exception to allow PostgreSQL fallback")
    void redisGetFailureIsAbsorbed() {
        CacheErrorHandler handler = cacheConfig.errorHandler();
        Cache mockCache = mock(Cache.class);
        when(mockCache.getName()).thenReturn("knowledge_articles");

        RedisConnectionFailureException redisDownEx =
                new RedisConnectionFailureException("Could not connect to Redis at 127.0.0.1:6379: Connection refused");

        assertThatCode(() -> handler.handleCacheGetError(redisDownEx, mockCache, 42L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("When Redis connection fails during cache PUT, error handler absorbs exception so database write succeeds")
    void redisPutFailureIsAbsorbed() {
        CacheErrorHandler handler = cacheConfig.errorHandler();
        Cache mockCache = mock(Cache.class);
        when(mockCache.getName()).thenReturn("teams");

        RedisConnectionFailureException redisDownEx =
                new RedisConnectionFailureException("Redis command timed out after 2000ms");

        assertThatCode(() -> handler.handleCachePutError(redisDownEx, mockCache, "team_1", "team-dto-payload"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("When Redis connection fails during cache EVICT, error handler absorbs exception")
    void redisEvictFailureIsAbsorbed() {
        CacheErrorHandler handler = cacheConfig.errorHandler();
        Cache mockCache = mock(Cache.class);
        when(mockCache.getName()).thenReturn("knowledge_articles");

        RedisConnectionFailureException redisDownEx =
                new RedisConnectionFailureException("Redis cluster node unreachable");

        assertThatCode(() -> handler.handleCacheEvictError(redisDownEx, mockCache, 100L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("When Redis connection fails during cache CLEAR, error handler absorbs exception")
    void redisClearFailureIsAbsorbed() {
        CacheErrorHandler handler = cacheConfig.errorHandler();
        Cache mockCache = mock(Cache.class);
        when(mockCache.getName()).thenReturn("team_members");

        RedisConnectionFailureException redisDownEx =
                new RedisConnectionFailureException("Read-only replica rejected flush command");

        assertThatCode(() -> handler.handleCacheClearError(redisDownEx, mockCache))
                .doesNotThrowAnyException();
    }
}