package com.resolveai.incident.controller;

import com.resolveai.common.dto.ApiResponse;
import com.resolveai.common.dto.PageResponse;
import com.resolveai.incident.dto.*;
import com.resolveai.incident.entity.IncidentStatus;
import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.entity.Severity;
import com.resolveai.incident.service.IncidentCommentService;
import com.resolveai.incident.service.IncidentHistoryService;
import com.resolveai.incident.service.IncidentService;
import com.resolveai.knowledge.dto.IncidentKnowledgeLinkResponse;
import com.resolveai.knowledge.service.IncidentKnowledgeLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller providing full core lifecycle endpoints for incident management.
 */
@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents", description = "Core incident management, lifecycle transitions, assignment, comments, and audit history")
@SecurityRequirement(name = "bearerAuth")
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentCommentService incidentCommentService;
    private final IncidentHistoryService incidentHistoryService;
    private final IncidentKnowledgeLinkService incidentKnowledgeLinkService;

    @PostMapping
    @Operation(summary = "Create a new incident", description = "Reports an incident with initial status NEW. The authenticated user is registered as the reporter.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Incident created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<IncidentResponse>> createIncident(
            @Valid @RequestBody IncidentCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        IncidentResponse response = incidentService.createIncident(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Incident reported successfully", response));
    }

    @GetMapping
    @Operation(summary = "List incidents", description = "Returns a paginated list of incidents scoped to the user's role and requested filters.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incidents retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> getIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long reporterId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<IncidentResponse> response = incidentService.getIncidents(
                status, priority, severity, categoryId, assigneeId, reporterId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get single incident by ID", description = "Returns detailed incident information subject to resource-level ownership authorization.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incident retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Caller lacks permission to view this incident"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    public ResponseEntity<ApiResponse<IncidentResponse>> getIncidentById(@PathVariable Long id) {
        IncidentResponse response = incidentService.getIncidentById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update incident details", description = "Modifies editable incident attributes (title, description, category, priority, severity).")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incident updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    public ResponseEntity<ApiResponse<IncidentResponse>> updateIncident(
            @PathVariable Long id,
            @Valid @RequestBody IncidentUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        IncidentResponse response = incidentService.updateIncident(id, request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Incident updated successfully", response));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Assign incident to engineer", description = "Assigns an incident to an active engineer and advances status to ASSIGNED if previously NEW or TRIAGED.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incident assigned successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid assignment request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Requires MANAGER or ADMIN role"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident or Engineer not found")
    })
    public ResponseEntity<ApiResponse<IncidentResponse>> assignIncident(
            @PathVariable Long id,
            @Valid @RequestBody IncidentAssignRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        IncidentResponse response = incidentService.assignIncident(id, request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Incident assigned successfully", response));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update incident status", description = "Transitions an incident along its lifecycle state machine and records audit history.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incident status updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - User not authorized for this transition"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    public ResponseEntity<ApiResponse<IncidentResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody IncidentStatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        IncidentResponse response = incidentService.updateStatus(id, request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Incident status updated successfully", response));
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add comment to incident", description = "Posts a customer update or internal note on an incident.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Comment added successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Not permitted to comment on this incident"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    public ResponseEntity<ApiResponse<IncidentCommentResponse>> addComment(
            @PathVariable Long id,
            @Valid @RequestBody IncidentCommentCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        IncidentCommentResponse response = incidentCommentService.addComment(id, request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Comment added successfully", response));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "List incident comments", description = "Retrieves comments for an incident. Internal notes are omitted for standard EMPLOYEE callers.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comments retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    public ResponseEntity<ApiResponse<List<IncidentCommentResponse>>> getComments(@PathVariable Long id) {
        List<IncidentCommentResponse> response = incidentCommentService.getComments(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get incident audit history", description = "Retrieves the chronological audit log of all transitions and updates for an incident.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Audit history retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    public ResponseEntity<ApiResponse<List<IncidentHistoryResponse>>> getHistory(@PathVariable Long id) {
        List<IncidentHistoryResponse> response = incidentHistoryService.getIncidentHistory(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{incidentId}/knowledge/{articleId}")
    @Operation(summary = "Link knowledge article to incident", description = "Associates an existing knowledge article with an incident.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Knowledge article linked successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident or Article not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflict - Article already linked to incident")
    })
    public ResponseEntity<ApiResponse<IncidentKnowledgeLinkResponse>> linkKnowledgeArticle(
            @PathVariable("incidentId") Long incidentId,
            @PathVariable("articleId") Long articleId,
            @AuthenticationPrincipal UserDetails userDetails) {

        IncidentKnowledgeLinkResponse response = incidentKnowledgeLinkService.linkArticleToIncident(
                incidentId, articleId, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Knowledge article linked to incident successfully", response));
    }

    @DeleteMapping("/{incidentId}/knowledge/{articleId}")
    @Operation(summary = "Unlink knowledge article from incident", description = "Removes the association between a knowledge article and an incident.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Knowledge article unlinked successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident, Article, or Link not found")
    })
    public ResponseEntity<ApiResponse<Void>> unlinkKnowledgeArticle(
            @PathVariable("incidentId") Long incidentId,
            @PathVariable("articleId") Long articleId,
            @AuthenticationPrincipal UserDetails userDetails) {

        incidentKnowledgeLinkService.unlinkArticleFromIncident(incidentId, articleId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Knowledge article unlinked successfully", null));
    }

    @GetMapping("/{incidentId}/knowledge")
    @Operation(summary = "Get knowledge articles linked to incident", description = "Retrieves all knowledge articles linked to the incident that the caller is authorized to view.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Linked knowledge articles retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    public ResponseEntity<ApiResponse<List<IncidentKnowledgeLinkResponse>>> getLinkedKnowledgeArticles(
            @PathVariable("incidentId") Long incidentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<IncidentKnowledgeLinkResponse> response = incidentKnowledgeLinkService.getLinkedArticles(
                incidentId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
