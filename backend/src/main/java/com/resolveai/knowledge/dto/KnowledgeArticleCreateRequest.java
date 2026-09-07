package com.resolveai.knowledge.dto;

import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating a new Knowledge Base Article.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeArticleCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 250, message = "Title must be between 3 and 250 characters")
    private String title;

    private String content;

    private String problem;

    private String symptoms;

    private String rootCause;

    private String resolution;

    private Long categoryId;

    @Size(max = 500, message = "Tags cannot exceed 500 characters")
    private String tags;

    private KnowledgeArticleStatus status;
}
