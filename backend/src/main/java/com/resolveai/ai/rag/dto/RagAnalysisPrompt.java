package com.resolveai.ai.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload sent to the AI Provider to construct a grounded diagnosis and resolution suggestion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagAnalysisPrompt {

    private Long incidentId;
    private String incidentNumber;
    private String title;
    private String description;
    private String currentCategory;
    private String currentPriority;
    private String currentSeverity;

    @Builder.Default
    private List<RagSourceItem> sources = new ArrayList<>();
}
