package com.resolveai.ai.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata and similarity citation for a grounding source utilized in RAG.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSourceDto {

    private RagSourceType sourceType;
    private Long sourceId;
    private String title;
    private Double similarity;
    private String reference;
    private String snippet;
}
