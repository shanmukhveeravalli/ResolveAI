package com.resolveai.ai.rag;

import com.resolveai.ai.config.AiConfigProperties;
import com.resolveai.ai.provider.EmbeddingProvider;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentStatus;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.knowledge.entity.KnowledgeArticle;
import com.resolveai.knowledge.repository.KnowledgeArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service managing non-blocking, isolated embedding synchronization.
 * Guarantees failure isolation: failures in embedding generation never roll back
 * or break normal incident resolution or knowledge article CRUD workflows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorEmbeddingSyncService {

    private final EmbeddingProvider embeddingProvider;
    private final VectorSearchService vectorSearchService;
    private final RagTextPreparationService ragTextPreparationService;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final IncidentRepository incidentRepository;
    private final AiConfigProperties aiConfigProperties;

    /**
     * Synchronizes vector embedding for a knowledge article.
     * Non-blocking to caller; safely handles all exceptions.
     */
    public void syncArticleEmbedding(Long articleId) {
        if (!aiConfigProperties.isEnabled()) {
            return;
        }

        try {
            KnowledgeArticle article = knowledgeArticleRepository.findById(articleId).orElse(null);
            if (article == null) {
                return;
            }

            String text = ragTextPreparationService.prepareKnowledgeArticleText(article);
            if (text.isBlank()) {
                return;
            }

            List<Double> embedding = embeddingProvider.generateEmbedding(text);
            vectorSearchService.storeArticleEmbedding(articleId, embedding, aiConfigProperties.getEmbeddingModel());
            log.info("Successfully updated vector embedding for knowledge article ID: {}", articleId);

        } catch (Exception e) {
            log.warn("Isolated embedding synchronization failed for article ID {}: {}", articleId, e.getMessage());
        }
    }

    /**
     * Synchronizes vector embedding for a historical incident (only for RESOLVED or CLOSED status).
     * Non-blocking to caller; safely handles all exceptions.
     */
    public void syncIncidentEmbedding(Long incidentId) {
        if (!aiConfigProperties.isEnabled()) {
            return;
        }

        try {
            Incident incident = incidentRepository.findById(incidentId).orElse(null);
            if (incident == null) {
                return;
            }

            if (incident.getStatus() != IncidentStatus.RESOLVED && incident.getStatus() != IncidentStatus.CLOSED) {
                return;
            }

            String text = ragTextPreparationService.prepareIncidentText(incident);
            if (text.isBlank()) {
                return;
            }

            List<Double> embedding = embeddingProvider.generateEmbedding(text);
            vectorSearchService.storeIncidentEmbedding(incidentId, embedding, aiConfigProperties.getEmbeddingModel());
            log.info("Successfully updated vector embedding for historical incident ID: {}", incidentId);

        } catch (Exception e) {
            log.warn("Isolated embedding synchronization failed for incident ID {}: {}", incidentId, e.getMessage());
        }
    }
}
