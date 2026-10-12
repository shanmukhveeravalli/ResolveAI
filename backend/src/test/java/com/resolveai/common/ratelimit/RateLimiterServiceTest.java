package com.resolveai.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        // Test in-memory fallback by providing null Redis template
        rateLimiterService = new RateLimiterService(null);
    }

    @Test
    @DisplayName("tryAcquire permits requests up to maxRequests limit")
    void permitsRequestsUpToLimit() {
        String key = "test:user1";
        int limit = 3;
        int period = 60;

        assertThat(rateLimiterService.tryAcquire(key, limit, period)).isTrue();
        assertThat(rateLimiterService.tryAcquire(key, limit, period)).isTrue();
        assertThat(rateLimiterService.tryAcquire(key, limit, period)).isTrue();

        // 4th request exceeds limit
        assertThat(rateLimiterService.tryAcquire(key, limit, period)).isFalse();
    }

    @Test
    @DisplayName("checkLimit throws RateLimitExceededException when limit is exceeded")
    void checkLimitThrowsException() {
        String key = "test:auth_login:192.168.1.50";
        int limit = 2;
        int period = 30;

        rateLimiterService.checkLimit(key, limit, period);
        rateLimiterService.checkLimit(key, limit, period);

        assertThatThrownBy(() -> rateLimiterService.checkLimit(key, limit, period))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    @DisplayName("Different keys maintain independent rate limit buckets")
    void separateKeysAreIndependent() {
        String key1 = "test:ip1";
        String key2 = "test:ip2";
        int limit = 1;
        int period = 60;

        assertThat(rateLimiterService.tryAcquire(key1, limit, period)).isTrue();
        assertThat(rateLimiterService.tryAcquire(key1, limit, period)).isFalse();

        // key2 should still be permitted
        assertThat(rateLimiterService.tryAcquire(key2, limit, period)).isTrue();
    }
}
