package com.resolveai.ai.rag;

import com.resolveai.incident.entity.Incident;
import com.resolveai.knowledge.entity.KnowledgeArticle;
import org.springframework.stereotype.Service;

/**
 * Deterministic text formulation service for generating vector embeddings.
 * Sanitizes input and ensures consistency across indexing and query execution.
 * Strictly excludes passwords, JWT tokens, hashes, and internal secrets.
 */
@Service
public class RagTextPreparationService {

    /**
     * Prepares deterministic textual content from a KnowledgeArticle for embedding generation.
     */
    public String prepareKnowledgeArticleText(KnowledgeArticle article) {
        if (article == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "Title: ", article.getTitle());
        if (article.getCategory() != null) {
            appendIfPresent(sb, "Category: ", article.getCategory().getName());
        }
        appendIfPresent(sb, "Problem: ", article.getProblem());
        appendIfPresent(sb, "Symptoms: ", article.getSymptoms());
        appendIfPresent(sb, "Root Cause: ", article.getRootCause());
        appendIfPresent(sb, "Resolution: ", article.getResolution());
        appendIfPresent(sb, "Content: ", article.getContent());
        appendIfPresent(sb, "Tags: ", article.getTags());

        return sb.toString().trim();
    }

    /**
     * Prepares deterministic textual content from an Incident for query embedding or indexing.
     */
    public String prepareIncidentText(Incident incident) {
        if (incident == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "Title: ", incident.getTitle());
        appendIfPresent(sb, "Description: ", incident.getDescription());
        if (incident.getCategory() != null) {
            appendIfPresent(sb, "Category: ", incident.getCategory().getName());
        }
        if (incident.getPriority() != null) {
            appendIfPresent(sb, "Priority: ", incident.getPriority().name());
        }
        if (incident.getSeverity() != null) {
            appendIfPresent(sb, "Severity: ", incident.getSeverity().name());
        }

        return sb.toString().trim();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(value.trim()).append("\n");
        }
    }
}
