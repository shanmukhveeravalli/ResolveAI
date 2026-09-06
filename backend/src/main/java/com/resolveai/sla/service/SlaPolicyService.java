package com.resolveai.sla.service;

import com.resolveai.common.exception.DuplicateResourceException;
import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.incident.entity.Priority;
import com.resolveai.sla.dto.SlaPolicyCreateRequest;
import com.resolveai.sla.dto.SlaPolicyResponse;
import com.resolveai.sla.dto.SlaPolicyUpdateRequest;
import com.resolveai.sla.entity.SlaPolicy;
import com.resolveai.sla.repository.SlaPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service managing configurable SLA policies per incident priority tier (P1-P4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaPolicyService {

    private final SlaPolicyRepository slaPolicyRepository;

    /**
     * Creates a new SLA Policy with target time validation and priority uniqueness enforcement.
     */
    @Transactional
    public SlaPolicyResponse createPolicy(SlaPolicyCreateRequest request) {
        if (request.getResolutionTimeMinutes() < request.getResponseTimeMinutes()) {
            throw new IllegalArgumentException(
                    "Resolution target time must be greater than or equal to response target time");
        }

        if (slaPolicyRepository.existsByPriority(request.getPriority())) {
            throw new DuplicateResourceException("SlaPolicy", "priority", request.getPriority());
        }

        SlaPolicy policy = SlaPolicy.builder()
                .name(request.getName().trim())
                .priority(request.getPriority())
                .responseTimeMinutes(request.getResponseTimeMinutes())
                .resolutionTimeMinutes(request.getResolutionTimeMinutes())
                .escalationThresholdMinutes(request.getEscalationThresholdMinutes())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        SlaPolicy saved = slaPolicyRepository.save(policy);
        log.info("Created SLA policy ID {} for priority {}", saved.getId(), saved.getPriority());

        return SlaPolicyResponse.fromEntity(saved);
    }

    /**
     * Updates editable targets or state of an existing SLA Policy.
     */
    @Transactional
    public SlaPolicyResponse updatePolicy(Long id, SlaPolicyUpdateRequest request) {
        SlaPolicy policy = slaPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SlaPolicy", id));

        int responseTime = request.getResponseTimeMinutes() != null
                ? request.getResponseTimeMinutes()
                : policy.getResponseTimeMinutes();

        int resolutionTime = request.getResolutionTimeMinutes() != null
                ? request.getResolutionTimeMinutes()
                : policy.getResolutionTimeMinutes();

        if (resolutionTime < responseTime) {
            throw new IllegalArgumentException(
                    "Resolution target time must be greater than or equal to response target time");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            policy.setName(request.getName().trim());
        }

        if (request.getResponseTimeMinutes() != null) {
            policy.setResponseTimeMinutes(request.getResponseTimeMinutes());
        }

        if (request.getResolutionTimeMinutes() != null) {
            policy.setResolutionTimeMinutes(request.getResolutionTimeMinutes());
        }

        if (request.getEscalationThresholdMinutes() != null) {
            policy.setEscalationThresholdMinutes(request.getEscalationThresholdMinutes());
        }

        if (request.getIsActive() != null) {
            policy.setIsActive(request.getIsActive());
        }

        SlaPolicy saved = slaPolicyRepository.save(policy);
        log.info("Updated SLA policy ID {} for priority {}", saved.getId(), saved.getPriority());

        return SlaPolicyResponse.fromEntity(saved);
    }

    /**
     * Retrieves a single SLA Policy by ID.
     */
    @Transactional(readOnly = true)
    public SlaPolicyResponse getPolicyById(Long id) {
        SlaPolicy policy = slaPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SlaPolicy", id));
        return SlaPolicyResponse.fromEntity(policy);
    }

    /**
     * Lists SLA Policies, optionally filtering to active policies only.
     */
    @Transactional(readOnly = true)
    public List<SlaPolicyResponse> getPolicies(Boolean activeOnly) {
        List<SlaPolicy> policies = (activeOnly != null && activeOnly)
                ? slaPolicyRepository.findByIsActiveTrue()
                : slaPolicyRepository.findAll();

        return policies.stream()
                .map(SlaPolicyResponse::fromEntity)
                .toList();
    }

    /**
     * Finds active policy by priority tier.
     */
    @Transactional(readOnly = true)
    public Optional<SlaPolicy> getPolicyForPriority(Priority priority) {
        return slaPolicyRepository.findByPriority(priority);
    }
}
