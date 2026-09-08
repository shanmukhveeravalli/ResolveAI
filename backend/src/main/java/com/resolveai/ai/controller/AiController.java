package com.resolveai.ai.controller;

import com.resolveai.ai.dto.IncidentAnalysisResponse;
import com.resolveai.ai.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller exposing AI-assisted advisory endpoints.
 * All suggestions are non-binding and require human approval before applying to incident state.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI Foundation", description = "AI-assisted triage, categorization, severity suggestion, and summarization")
@SecurityRequirement(name = "bearerAuth")
public class AiController {

    private final AiService aiService;

    /**
     * Performs AI-assisted triage and analysis for an incident.
     *
     * @param incidentId target incident identifier
     * @return structured advisory response
     */
    @PostMapping("/incidents/{incidentId}/analyze")
    @PreAuthorize("hasAnyRole('ENGINEER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Analyze incident with AI", description = "Generates advisory categorization, priority, severity suggestions, and summary. Does not modify incident state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analysis generated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient role or incident permissions"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "503", description = "AI service unavailable, disabled, or provider failure")
    })
    public ResponseEntity<IncidentAnalysisResponse> analyzeIncident(@PathVariable Long incidentId) {
        IncidentAnalysisResponse response = aiService.analyzeIncident(incidentId);
        return ResponseEntity.ok(response);
    }
}
