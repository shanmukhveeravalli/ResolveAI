package com.resolveai.incident.service;

import com.resolveai.auth.security.AuthorizationService;
import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.incident.dto.IncidentHistoryResponse;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentHistory;
import com.resolveai.incident.repository.IncidentHistoryRepository;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing immutable audit history records for incidents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentHistoryService {

    private final IncidentHistoryRepository incidentHistoryRepository;
    private final IncidentRepository incidentRepository;
    private final AuthorizationService authorizationService;

    /**
     * Records an audit event for an incident.
     */
    @Transactional
    public IncidentHistory recordHistory(Incident incident,
                                        User actor,
                                        String actionType,
                                        String fieldName,
                                        String oldValue,
                                        String newValue) {

        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actor(actor)
                .actionType(actionType)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();

        return incidentHistoryRepository.save(history);
    }

    /**
     * Retrieves the audit history for an incident subject to authorization.
     */
    @Transactional(readOnly = true)
    public List<IncidentHistoryResponse> getIncidentHistory(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        if (!authorizationService.canAccessIncident(incident)) {
            throw new AccessDeniedException("Access denied: insufficient permissions to view audit history for incident ID: " + incidentId);
        }

        List<IncidentHistory> historyList = incidentHistoryRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId);
        return historyList.stream()
                .map(IncidentHistoryResponse::fromEntity)
                .toList();
    }
}
