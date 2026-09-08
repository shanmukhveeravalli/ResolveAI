package com.resolveai.ai.dto;

import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.entity.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured Advisory Response from AI incident analysis.
 * Note: These fields are non-binding suggestions only and do not automatically alter database incident state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentAnalysisResponse {

    private Long incidentId;
    private String suggestedCategory;
    private Priority suggestedPriority;
    private Severity suggestedSeverity;
    private String summary;
    private String analysis;
}
