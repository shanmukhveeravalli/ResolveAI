package com.resolveai.common.config;

import com.resolveai.knowledge.dto.KnowledgeArticleResponse;
import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import com.resolveai.team.dto.TeamResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    @Test
    @DisplayName("Verify cache constants and TTL settings")
    void cacheConstantsAndTtls() {
        assertThat(CacheConfig.CACHE_KNOWLEDGE_ARTICLES).isEqualTo("knowledge_articles");
        assertThat(CacheConfig.CACHE_TEAMS).isEqualTo("teams");
        assertThat(CacheConfig.CACHE_TEAM_MEMBERS).isEqualTo("team_members");

        assertThat(CacheConfig.DEFAULT_TTL).isEqualTo(Duration.ofMinutes(15));
        assertThat(CacheConfig.KNOWLEDGE_ARTICLES_TTL).isEqualTo(Duration.ofMinutes(30));
        assertThat(CacheConfig.TEAMS_TTL).isEqualTo(Duration.ofHours(1));
        assertThat(CacheConfig.TEAM_MEMBERS_TTL).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("Verify Redis value serializer handles DTOs with Java 8/Instant dates")
    void redisValueSerializerSerializesAndDeserializes() {
        GenericJackson2JsonRedisSerializer serializer = cacheConfig.redisValueSerializer();

        KnowledgeArticleResponse article = KnowledgeArticleResponse.builder()
                .id(42L)
                .title("Database connection pool tuning")
                .slug("database-connection-pool-tuning")
                .content("Detailed instructions on HikariCP configuration")
                .status(KnowledgeArticleStatus.PUBLISHED)
                .publishedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        byte[] serialized = serializer.serialize(article);
        assertThat(serialized).isNotEmpty();

        Object deserialized = serializer.deserialize(serialized);
        assertThat(deserialized).isInstanceOf(KnowledgeArticleResponse.class);

        KnowledgeArticleResponse casted = (KnowledgeArticleResponse) deserialized;
        assertThat(casted.getId()).isEqualTo(42L);
        assertThat(casted.getTitle()).isEqualTo("Database connection pool tuning");
        assertThat(casted.getStatus()).isEqualTo(KnowledgeArticleStatus.PUBLISHED);

        TeamResponse team = TeamResponse.builder()
                .id(10L)
                .name("Core Infrastructure")
                .description("Team handling SRE and databases")
                .isActive(true)
                .memberCount(5)
                .createdAt(Instant.now())
                .build();

        byte[] teamBytes = serializer.serialize(team);
        assertThat(teamBytes).isNotEmpty();

        Object teamDeserialized = serializer.deserialize(teamBytes);
        assertThat(teamDeserialized).isInstanceOf(TeamResponse.class);
        TeamResponse castedTeam = (TeamResponse) teamDeserialized;
        assertThat(castedTeam.getId()).isEqualTo(10L);
        assertThat(castedTeam.getName()).isEqualTo("Core Infrastructure");
    }

    @Test
    @DisplayName("Verify resilient CacheErrorHandler does not propagate runtime exceptions")
    void cacheErrorHandlerDoesNotThrowExceptions() {
        CacheErrorHandler handler = cacheConfig.errorHandler();
        RuntimeException dummyEx = new RuntimeException("Redis connection refused on 127.0.0.1:6379");

        assertThatCode(() -> handler.handleCacheGetError(dummyEx, null, "test-key"))
                .doesNotThrowAnyException();

        assertThatCode(() -> handler.handleCachePutError(dummyEx, null, "test-key", "test-val"))
                .doesNotThrowAnyException();

        assertThatCode(() -> handler.handleCacheEvictError(dummyEx, null, "test-key"))
                .doesNotThrowAnyException();

        assertThatCode(() -> handler.handleCacheClearError(dummyEx, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Verify RedisCacheManager builds and configures explicit caches")
    void redisCacheManagerConfig() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        GenericJackson2JsonRedisSerializer serializer = cacheConfig.redisValueSerializer();

        RedisCacheManager cacheManager = cacheConfig.redisCacheManager(connectionFactory, serializer);
        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.CACHE_KNOWLEDGE_ARTICLES)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.CACHE_TEAMS)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.CACHE_TEAM_MEMBERS)).isNotNull();
    }
}