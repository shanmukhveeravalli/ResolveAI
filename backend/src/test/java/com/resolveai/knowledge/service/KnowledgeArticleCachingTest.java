package com.resolveai.knowledge.service;

import com.resolveai.auth.security.AuthorizationService;
import com.resolveai.common.config.CacheConfig;
import com.resolveai.knowledge.dto.KnowledgeArticleResponse;
import com.resolveai.knowledge.dto.KnowledgeArticleUpdateRequest;
import com.resolveai.knowledge.entity.KnowledgeArticle;
import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import com.resolveai.knowledge.repository.KnowledgeArticleRepository;
import com.resolveai.auth.entity.Role;
import com.resolveai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class KnowledgeArticleCachingTest {

    @Autowired
    private KnowledgeArticleService knowledgeArticleService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private KnowledgeArticleRepository knowledgeArticleRepository;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private com.resolveai.ai.rag.VectorEmbeddingSyncService vectorEmbeddingSyncService;

    private KnowledgeArticle testArticle;
    private User author;

    @BeforeEach
    void setUp() {
        // Clear knowledge article cache prior to each test
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_KNOWLEDGE_ARTICLES);
        if (cache != null) {
            cache.clear();
        }

        Role role = Role.builder().id(1L).name("ENGINEER").build();
        author = User.builder().id(100L).email("engineer@resolveai.com").firstName("Jane").lastName("Dev").role(role).build();

        testArticle = KnowledgeArticle.builder()
                .id(1L)
                .title("Microservice Circuit Breaking")
                .slug("microservice-circuit-breaking")
                .content("Guide on resilience4j")
                .problem("Cascading failures")
                .symptoms("High 504 rates")
                .rootCause("Downstream timeouts")
                .resolution("Use circuit breaker")
                .status(KnowledgeArticleStatus.DRAFT)
                .author(author)
                .build();

        when(authorizationService.canViewKnowledgeArticle(any())).thenReturn(true);
        when(authorizationService.canManageKnowledgeArticle(any())).thenReturn(true);
    }

    @Test
    @DisplayName("getArticleById caches response and subsequent call hits cache without querying repository")
    void getArticleByIdCaching() {
        when(knowledgeArticleRepository.findById(1L)).thenReturn(Optional.of(testArticle));

        // First call - should query repository
        KnowledgeArticleResponse response1 = knowledgeArticleService.getArticleById(1L);
        assertThat(response1).isNotNull();
        assertThat(response1.getId()).isEqualTo(1L);
        verify(knowledgeArticleRepository, times(1)).findById(1L);

        // Cache must have stored the response
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_KNOWLEDGE_ARTICLES);
        assertThat(cache).isNotNull();
        assertThat(cache.get(1L)).isNotNull();

        // Second call - should hit cache, repository not queried again
        KnowledgeArticleResponse response2 = knowledgeArticleService.getArticleById(1L);
        assertThat(response2).isNotNull();
        assertThat(response2.getId()).isEqualTo(1L);
        verify(knowledgeArticleRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("updateArticle evicts cached article")
    void updateArticleEvictsCache() {
        when(knowledgeArticleRepository.findById(1L)).thenReturn(Optional.of(testArticle));
        when(knowledgeArticleRepository.save(any(KnowledgeArticle.class))).thenReturn(testArticle);

        // Populate cache
        knowledgeArticleService.getArticleById(1L);
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_KNOWLEDGE_ARTICLES);
        assertThat(cache.get(1L)).isNotNull();

        // Update article -> evicts cache
        KnowledgeArticleUpdateRequest updateReq = new KnowledgeArticleUpdateRequest();
        updateReq.setTitle("Updated Title");
        knowledgeArticleService.updateArticle(1L, updateReq, "engineer@resolveai.com");

        // Cache should now be evicted
        assertThat(cache.get(1L)).isNull();
    }

    @Test
    @DisplayName("publishArticle evicts cached article")
    void publishArticleEvictsCache() {
        when(knowledgeArticleRepository.findById(1L)).thenReturn(Optional.of(testArticle));
        when(knowledgeArticleRepository.save(any(KnowledgeArticle.class))).thenReturn(testArticle);

        // Populate cache
        knowledgeArticleService.getArticleById(1L);
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_KNOWLEDGE_ARTICLES);
        assertThat(cache.get(1L)).isNotNull();

        // Publish article
        knowledgeArticleService.publishArticle(1L, "engineer@resolveai.com");

        // Cache should be evicted
        assertThat(cache.get(1L)).isNull();
    }

    @Test
    @DisplayName("archiveArticle evicts cached article")
    void archiveArticleEvictsCache() {
        when(knowledgeArticleRepository.findById(1L)).thenReturn(Optional.of(testArticle));
        when(knowledgeArticleRepository.save(any(KnowledgeArticle.class))).thenReturn(testArticle);

        // Populate cache
        knowledgeArticleService.getArticleById(1L);
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_KNOWLEDGE_ARTICLES);
        assertThat(cache.get(1L)).isNotNull();

        // Archive article
        knowledgeArticleService.archiveArticle(1L, "engineer@resolveai.com");

        // Cache should be evicted
        assertThat(cache.get(1L)).isNull();
    }
}