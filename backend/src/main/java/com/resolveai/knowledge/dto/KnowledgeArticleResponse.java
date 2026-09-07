package com.resolveai.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resolveai.knowledge.entity.KnowledgeArticle;
import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Enterprise Knowledge Article Response DTO hiding entity internals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeArticleResponse {

    private Long id;
    private String title;
    private String slug;
    private String content;
    private String problem;
    private String symptoms;
    private String rootCause;
    private String resolution;

    private Long categoryId;
    private String categoryName;
    private String categorySlug;

    private Long authorId;
    private String authorName;
    private String authorEmail;

    private KnowledgeArticleStatus status;
    private String tags;

    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static KnowledgeArticleResponse fromEntity(KnowledgeArticle article) {
        if (article == null) {
            return null;
        }

        KnowledgeArticleResponseBuilder builder = KnowledgeArticleResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .slug(article.getSlug())
                .content(article.getContent() != null ? article.getContent() : article.getResolution())
                .problem(article.getProblem())
                .symptoms(article.getSymptoms())
                .rootCause(article.getRootCause())
                .resolution(article.getResolution())
                .status(article.getStatus())
                .tags(article.getTags())
                .publishedAt(article.getPublishedAt())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt());

        if (article.getCategory() != null) {
            builder.categoryId(article.getCategory().getId())
                    .categoryName(article.getCategory().getName())
                    .categorySlug(article.getCategory().getSlug());
        }

        if (article.getAuthor() != null) {
            builder.authorId(article.getAuthor().getId())
                    .authorName(article.getAuthor().getFirstName() + " " + article.getAuthor().getLastName())
                    .authorEmail(article.getAuthor().getEmail());
        }

        return builder.build();
    }
}
