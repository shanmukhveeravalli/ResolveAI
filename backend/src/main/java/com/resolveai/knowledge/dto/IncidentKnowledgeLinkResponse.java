package com.resolveai.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resolveai.knowledge.entity.IncidentKnowledgeLink;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Enterprise Response DTO for incident-to-knowledge article link.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncidentKnowledgeLinkResponse {

    private Long id;

    private Long incidentId;
    private String incidentNumber;
    private String incidentTitle;

    private Long articleId;
    private String articleTitle;
    private String articleSlug;
    private KnowledgeArticleResponse article;

    private BigDecimal relevanceScore;
    private Boolean linkedByAi;
    private Instant createdAt;

    public static IncidentKnowledgeLinkResponse fromEntity(IncidentKnowledgeLink link) {
        if (link == null) {
            return null;
        }

        IncidentKnowledgeLinkResponseBuilder builder = IncidentKnowledgeLinkResponse.builder()
                .id(link.getId())
                .relevanceScore(link.getRelevanceScore())
                .linkedByAi(link.getLinkedByAi())
                .createdAt(link.getCreatedAt());

        if (link.getIncident() != null) {
            builder.incidentId(link.getIncident().getId())
                    .incidentNumber(link.getIncident().getIncidentNumber())
                    .incidentTitle(link.getIncident().getTitle());
        }

        if (link.getArticle() != null) {
            builder.articleId(link.getArticle().getId())
                    .articleTitle(link.getArticle().getTitle())
                    .articleSlug(link.getArticle().getSlug())
                    .article(KnowledgeArticleResponse.fromEntity(link.getArticle()));
        }

        return builder.build();
    }
}
