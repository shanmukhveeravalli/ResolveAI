package com.resolveai.analytics.controller;

import com.resolveai.analytics.dto.*;
import com.resolveai.analytics.service.AnalyticsService;
import com.resolveai.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Operational metrics and analytics for ResolveAI")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(summary = "Get Incident Overview", description = "Retrieves high-level counts of incidents with optional date filtering.")
    public ResponseEntity<ApiResponse<AnalyticsOverviewResponse>> getOverview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getIncidentOverview(parseFrom(from), parseTo(to))));
    }

    @GetMapping("/incidents/status")
    @Operation(summary = "Get Incident Status Breakdown", description = "Retrieves counts of incidents grouped by status.")
    public ResponseEntity<ApiResponse<List<StatusCountResponse>>> getStatusBreakdown(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getStatusBreakdown(parseFrom(from), parseTo(to))));
    }

    @GetMapping("/incidents/priority")
    @Operation(summary = "Get Incident Priority Breakdown", description = "Retrieves counts of incidents grouped by priority.")
    public ResponseEntity<ApiResponse<List<PriorityCountResponse>>> getPriorityBreakdown(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getPriorityBreakdown(parseFrom(from), parseTo(to))));
    }

    @GetMapping("/incidents/severity")
    @Operation(summary = "Get Incident Severity Breakdown", description = "Retrieves counts of incidents grouped by severity.")
    public ResponseEntity<ApiResponse<List<SeverityCountResponse>>> getSeverityBreakdown(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getSeverityBreakdown(parseFrom(from), parseTo(to))));
    }

    @GetMapping("/teams/workload")
    @Operation(summary = "Get Team Workload", description = "Retrieves assigned open incidents count per team.")
    public ResponseEntity<ApiResponse<List<TeamWorkloadResponse>>> getTeamWorkload() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getTeamWorkload()));
    }

    @GetMapping("/sla")
    @Operation(summary = "Get SLA Analytics", description = "Retrieves SLA breach metrics.")
    public ResponseEntity<ApiResponse<SlaAnalyticsResponse>> getSlaMetrics() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getSlaMetrics()));
    }

    @GetMapping("/knowledge")
    @Operation(summary = "Get Knowledge Base Analytics", description = "Retrieves counts of knowledge base articles by status.")
    public ResponseEntity<ApiResponse<KnowledgeAnalyticsResponse>> getKnowledgeMetrics() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getKnowledgeMetrics()));
    }

    private Instant parseFrom(String from) {
        if (from == null || from.isBlank()) {
            return null;
        }
        String trimmed = from.trim();
        try {
            if (trimmed.contains("T")) {
                return Instant.parse(trimmed);
            }
            return LocalDate.parse(trimmed).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format for 'from': " + trimmed + ". Expected YYYY-MM-DD or ISO-8601 date-time.");
        }
    }

    private Instant parseTo(String to) {
        if (to == null || to.isBlank()) {
            return null;
        }
        String trimmed = to.trim();
        try {
            if (trimmed.contains("T")) {
                return Instant.parse(trimmed);
            }
            return LocalDate.parse(trimmed).atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format for 'to': " + trimmed + ". Expected YYYY-MM-DD or ISO-8601 date-time.");
        }
    }
}
