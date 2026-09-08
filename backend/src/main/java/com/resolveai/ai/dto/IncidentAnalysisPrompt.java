package com.resolveai.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contextual payload extracted from an Incident to formulate the AI prompt.
 * Contains only non-sensitive operational fields (no passwords, JWTs, or internal secrets).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentAnalysisPrompt {

    private Long incidentId;
    private String incidentNumber;
    private String title;
    private String description;
    private String currentCategory;
    private String currentPriority;
    private String currentSeverity;
}
