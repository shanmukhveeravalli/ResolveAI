package com.resolveai.knowledge.service;

import com.resolveai.auth.security.AuthorizationService;
import com.resolveai.common.exception.DuplicateResourceException;
import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.knowledge.dto.IncidentKnowledgeLinkResponse;
import com.resolveai.knowledge.entity.IncidentKnowledgeLink;
import com.resolveai.knowledge.entity.KnowledgeArticle;
import com.resolveai.knowledge.repository.IncidentKnowledgeLinkRepository;
import com.resolveai.knowledge.repository.KnowledgeArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service managing relational links between Incidents and Knowledge Base articles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentKnowledgeLinkService {

    private final IncidentKnowledgeLinkRepository incidentKnowledgeLinkRepository;
    private final IncidentRepository incidentRepository;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final AuthorizationService authorizationService;

    /**
     * Links a knowledge article to an incident.
     * Enforces existence, authorization, visibility, and duplicate prevention.
     */
    @Transactional
    public IncidentKnowledgeLinkResponse linkArticleToIncident(Long incidentId, Long articleId, String currentUserEmail) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        KnowledgeArticle article = knowledgeArticleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArticle", articleId));

        if (!authorizationService.canAccessIncident(incident)) {
            throw new AccessDeniedException("Access denied: cannot access incident ID " + incidentId);
        }

        if (!authorizationService.canViewKnowledgeArticle(article)) {
            throw new AccessDeniedException("Access denied: cannot access knowledge article ID " + articleId);
        }

        if (incidentKnowledgeLinkRepository.existsByIncidentIdAndArticleId(incidentId, articleId)) {
            throw new DuplicateResourceException(
                    String.format("Knowledge article ID %d is already linked to incident ID %d", articleId, incidentId));
        }

        IncidentKnowledgeLink link = IncidentKnowledgeLink.builder()
                .incident(incident)
                .article(article)
                .relevanceScore(BigDecimal.valueOf(1.0000))
                .linkedByAi(false)
                .build();

        IncidentKnowledgeLink saved = incidentKnowledgeLinkRepository.save(link);
        log.info("Linked knowledge article ID {} to incident ID {} by {}", articleId, incidentId, currentUserEmail);

        return IncidentKnowledgeLinkResponse.fromEntity(saved);
    }

    /**
     * Removes an existing link between an incident and a knowledge article.
     */
    @Transactional
    public void unlinkArticleFromIncident(Long incidentId, Long articleId, String currentUserEmail) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        KnowledgeArticle article = knowledgeArticleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArticle", articleId));

        if (!authorizationService.canAccessIncident(incident)) {
            throw new AccessDeniedException("Access denied: cannot access incident ID " + incidentId);
        }

        IncidentKnowledgeLink link = incidentKnowledgeLinkRepository.findByIncidentIdAndArticleId(incidentId, articleId)
                .orElseThrow(() -> new ResourceNotFoundException("IncidentKnowledgeLink",
                        "incidentId=" + incidentId + ", articleId=" + articleId));

        incidentKnowledgeLinkRepository.delete(link);
        log.info("Unlinked knowledge article ID {} from incident ID {} by {}", articleId, incidentId, currentUserEmail);
    }

    /**
     * Retrieves all knowledge articles linked to an incident, filtered by caller visibility.
     */
    @Transactional(readOnly = true)
    public List<IncidentKnowledgeLinkResponse> getLinkedArticles(Long incidentId, String currentUserEmail) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        if (!authorizationService.canAccessIncident(incident)) {
            throw new AccessDeniedException("Access denied: cannot access incident ID " + incidentId);
        }

        List<IncidentKnowledgeLink> links = incidentKnowledgeLinkRepository.findByIncidentId(incidentId);

        return links.stream()
                .filter(link -> authorizationService.canViewKnowledgeArticle(link.getArticle()))
                .map(IncidentKnowledgeLinkResponse::fromEntity)
                .toList();
    }
}
