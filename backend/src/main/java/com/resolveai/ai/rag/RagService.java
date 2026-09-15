package com.resolveai.ai.rag;

import com.resolveai.ai.config.AiConfigProperties;
import com.resolveai.ai.exception.AiServiceUnavailableException;
import com.resolveai.ai.provider.AiProvider;
import com.resolveai.ai.provider.EmbeddingProvider;
import com.resolveai.ai.rag.dto.RagAnalysisPrompt;
import com.resolveai.ai.rag.dto.RagAnalysisResponse;
import com.resolveai.ai.rag.dto.RagSourceItem;
import com.resolveai.auth.security.AuthorizationService;
import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.knowledge.entity.KnowledgeArticle;
import com.resolveai.knowledge.repository.KnowledgeArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service orchestrating grounded Retrieval-Augmented Generation (RAG) analysis for incidents.
 * Enforces resource authorization, source-level visibility (no draft/private articles leaked),
 * failure isolation, and strict non-mutation of incident records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final IncidentRepository incidentRepository;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final AuthorizationService authorizationService;
    private final EmbeddingProvider embeddingProvider;
    private final VectorSearchService vectorSearchService;
    private final RagTextPreparationService ragTextPreparationService;
    private final AiProvider aiProvider;
    private final AiConfigProperties aiConfigProperties;

    /**
     * Performs grounded RAG analysis for the given incident.
     * Advisory only: does not modify incident status, priority, severity, category, or assignments.
     *
     * @param incidentId target incident identifier
     * @return grounded diagnosis, resolution recommendations, and cited source metadata
     */
    @Transactional(readOnly = true)
    public RagAnalysisResponse analyzeIncidentWithRag(Long incidentId) {
        if (!aiConfigProperties.isEnabled()) {
            throw new AiServiceUnavailableException("AI service is currently disabled. Enable with resolveai.ai.enabled=true");
        }

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        if (!authorizationService.canAccessIncident(incident)) {
            throw new AccessDeniedException("Access denied: insufficient permissions to analyze incident ID: " + incidentId);
        }

        // 1. Formulate sanitized incident query text
        String queryText = ragTextPreparationService.prepareIncidentText(incident);

        // 2. Generate vector embedding for incident query
        log.info("Generating query embedding for incident ID: {}", incidentId);
        List<Double> queryEmbedding = embeddingProvider.generateEmbedding(queryText);

        int topK = aiConfigProperties.getTopK() > 0 ? aiConfigProperties.getTopK() : 5;

        // 3. Vector similarity search on Knowledge Articles (respecting caller visibility)
        List<RagSourceItem> articleSources = vectorSearchService.searchSimilarArticles(queryEmbedding, topK);
        List<RagSourceItem> authorizedArticles = new ArrayList<>();
        for (RagSourceItem item : articleSources) {
            KnowledgeArticle article = knowledgeArticleRepository.findById(item.getSourceId()).orElse(null);
            if (article != null && authorizationService.canViewKnowledgeArticle(article)) {
                authorizedArticles.add(item);
            }
        }

        // 4. Vector similarity search on Historical Incidents (RESOLVED / CLOSED)
        List<RagSourceItem> incidentSources = vectorSearchService.searchSimilarIncidents(queryEmbedding, topK, incidentId);

        // 5. Aggregate and rank candidate sources by similarity score
        List<RagSourceItem> combinedSources = new ArrayList<>();
        combinedSources.addAll(authorizedArticles);
        combinedSources.addAll(incidentSources);
        combinedSources.sort(Comparator.comparingDouble(RagSourceItem::getSimilarity).reversed());

        List<RagSourceItem> topSources = combinedSources.stream().limit(topK).toList();

        // 6. Formulate grounded prompt payload
        RagAnalysisPrompt prompt = RagAnalysisPrompt.builder()
                .incidentId(incident.getId())
                .incidentNumber(incident.getIncidentNumber())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .currentCategory(incident.getCategory() != null ? incident.getCategory().getName() : null)
                .currentPriority(incident.getPriority() != null ? incident.getPriority().name() : null)
                .currentSeverity(incident.getSeverity() != null ? incident.getSeverity().name() : null)
                .sources(topSources)
                .build();

        // 7. Dispatch to AI Provider
        log.info("Dispatching RAG prompt for incident ID: {} with {} grounded sources", incidentId, topSources.size());
        return aiProvider.generateRagAnalysis(prompt);
    }
}
