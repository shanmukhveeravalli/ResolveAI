package com.resolveai.knowledge.repository;

import com.resolveai.knowledge.entity.IncidentKnowledgeLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentKnowledgeLinkRepository extends JpaRepository<IncidentKnowledgeLink, Long> {
    List<IncidentKnowledgeLink> findByIncidentId(Long incidentId);
    List<IncidentKnowledgeLink> findByArticleId(Long articleId);
    Optional<IncidentKnowledgeLink> findByIncidentIdAndArticleId(Long incidentId, Long articleId);
    boolean existsByIncidentIdAndArticleId(Long incidentId, Long articleId);
}
