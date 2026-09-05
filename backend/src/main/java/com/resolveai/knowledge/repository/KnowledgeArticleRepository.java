package com.resolveai.knowledge.repository;

import com.resolveai.knowledge.entity.KnowledgeArticle;
import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {
    Optional<KnowledgeArticle> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Page<KnowledgeArticle> findByStatus(KnowledgeArticleStatus status, Pageable pageable);
    Page<KnowledgeArticle> findByCategoryIdAndStatus(Long categoryId, KnowledgeArticleStatus status, Pageable pageable);

    @Query("SELECT k FROM KnowledgeArticle k WHERE k.status = 'PUBLISHED' AND " +
           "(LOWER(k.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(k.problem) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(k.symptoms) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<KnowledgeArticle> searchPublishedArticles(@Param("query") String query, Pageable pageable);
}
