package com.resolveai.ai.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal representation of a candidate source item retrieved via vector similarity search.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSourceItem {

    private RagSourceType sourceType;
    private Long sourceId;
    private String title;
    private String content;
    private Double similarity;
    private String reference;
}
