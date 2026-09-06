package com.resolveai.sla.controller;

import com.resolveai.auth.security.AuthorizationService;
import com.resolveai.common.dto.ApiResponse;
import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.sla.dto.SlaPolicyCreateRequest;
import com.resolveai.sla.dto.SlaPolicyResponse;
import com.resolveai.sla.dto.SlaPolicyUpdateRequest;
import com.resolveai.sla.dto.SlaRecordResponse;
import com.resolveai.sla.service.SlaPolicyService;
import com.resolveai.sla.service.SlaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing SLA policies and inspecting incident SLA compliance records.
 */
@RestController
@RequestMapping("/api/sla")
@RequiredArgsConstructor
@Tag(name = "SLA", description = "SLA policy configuration and incident SLA tracking")
@SecurityRequirement(name = "bearerAuth")
public class SlaController {

    private final SlaPolicyService slaPolicyService;
    private final SlaService slaService;
    private final IncidentRepository incidentRepository;
    private final AuthorizationService authorizationService;

    @GetMapping("/policies")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List SLA policies", description = "Retrieves all active or configured SLA policies.")
    public ResponseEntity<ApiResponse<List<SlaPolicyResponse>>> getPolicies(
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly) {
        List<SlaPolicyResponse> policies = slaPolicyService.getPolicies(activeOnly);
        return ResponseEntity.ok(ApiResponse.success(policies));
    }

    @GetMapping("/policies/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get SLA policy by ID", description = "Retrieves a specific SLA policy configuration.")
    public ResponseEntity<ApiResponse<SlaPolicyResponse>> getPolicyById(@PathVariable Long id) {
        SlaPolicyResponse policy = slaPolicyService.getPolicyById(id);
        return ResponseEntity.ok(ApiResponse.success(policy));
    }

    @PostMapping("/policies")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Create SLA policy", description = "Creates a new SLA policy. Restricted to MANAGER and ADMIN.")
    public ResponseEntity<ApiResponse<SlaPolicyResponse>> createPolicy(
            @Valid @RequestBody SlaPolicyCreateRequest request) {
        SlaPolicyResponse policy = slaPolicyService.createPolicy(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("SLA Policy created successfully", policy));
    }

    @PutMapping("/policies/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Update SLA policy", description = "Updates targets of an existing SLA policy. Restricted to MANAGER and ADMIN.")
    public ResponseEntity<ApiResponse<SlaPolicyResponse>> updatePolicy(
            @PathVariable Long id,
            @Valid @RequestBody SlaPolicyUpdateRequest request) {
        SlaPolicyResponse policy = slaPolicyService.updatePolicy(id, request);
        return ResponseEntity.ok(ApiResponse.success("SLA Policy updated successfully", policy));
    }

    @GetMapping("/records/incident/{incidentId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get SLA record for incident", description = "Retrieves SLA tracking record and breach status for an incident.")
    public ResponseEntity<ApiResponse<SlaRecordResponse>> getSlaRecordForIncident(@PathVariable Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        if (!authorizationService.canAccessIncident(incident)) {
            throw new AccessDeniedException("Access denied: insufficient permissions to view SLA for incident ID: " + incidentId);
        }

        SlaRecordResponse response = slaService.getSlaRecordByIncidentId(incidentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
