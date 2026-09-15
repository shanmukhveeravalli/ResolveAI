package com.resolveai.ai.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured Advisory Response containing AI-generated grounded diagnosis, resolution,
 * confidence score, and the precise list of cited grounding sources.
 * Advisory only: Does not automatically alter ticket state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagAnalysisResponse {

    private Long incidentId;
    private String diagnosis;
    private String resolutionSuggestion;
    private Double confidence;

    @Builder.Default
    private List<RagSourceDto> sources = new ArrayList<>();
}
