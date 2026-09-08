package com.resolveai.ai.service;

import com.resolveai.ai.config.AiConfigProperties;
import com.resolveai.ai.dto.IncidentAnalysisPrompt;
import com.resolveai.ai.dto.IncidentAnalysisResponse;
import com.resolveai.ai.exception.AiServiceUnavailableException;
import com.resolveai.ai.provider.AiProvider;
import com.resolveai.auth.security.AuthorizationService;
import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service orchestrating AI incident assistance.
 * Enforces resource authorization, failure isolation, and strict non-mutation of incidents.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final IncidentRepository incidentRepository;
    private final AuthorizationService authorizationService;
    private final AiProvider aiProvider;
    private final AiConfigProperties aiConfigProperties;

    /**
     * Performs AI-assisted triage and analysis for an incident.
     * The incident entity is never modified by this operation.
     *
     * @param incidentId target incident identifier
     * @return non-binding AI triage suggestions
     */
    @Transactional(readOnly = true)
    public IncidentAnalysisResponse analyzeIncident(Long incidentId) {
        if (!aiConfigProperties.isEnabled()) {
            throw new AiServiceUnavailableException("AI service is currently disabled. Enable with resolveai.ai.enabled=true");
        }

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        if (!authorizationService.canAccessIncident(incident)) {
            throw new AccessDeniedException("Access denied: insufficient permissions to analyze incident ID: " + incidentId);
        }

        IncidentAnalysisPrompt prompt = IncidentAnalysisPrompt.builder()
                .incidentId(incident.getId())
                .incidentNumber(incident.getIncidentNumber())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .currentCategory(incident.getCategory() != null ? incident.getCategory().getName() : null)
                .currentPriority(incident.getPriority() != null ? incident.getPriority().name() : null)
                .currentSeverity(incident.getSeverity() != null ? incident.getSeverity().name() : null)
                .build();

        log.info("Requesting AI analysis for incident ID: {}", incidentId);
        return aiProvider.analyzeIncident(prompt);
    }
}
