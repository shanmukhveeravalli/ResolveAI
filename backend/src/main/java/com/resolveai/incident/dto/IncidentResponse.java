package com.resolveai.incident.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentStatus;
import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.entity.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Enterprise Incident Response DTO hiding entity internals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncidentResponse {

    private Long id;
    private String incidentNumber;
    private String title;
    private String description;
    private IncidentStatus status;
    private Priority priority;
    private Severity severity;

    private Long categoryId;
    private String categoryName;
    private String categorySlug;

    private Long reporterId;
    private String reporterName;
    private String reporterEmail;

    private Long assigneeId;
    private String assigneeName;
    private String assigneeEmail;

    private Long teamId;
    private String teamName;

    private Instant resolvedAt;
    private Instant closedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static IncidentResponse fromEntity(Incident incident) {
        if (incident == null) {
            return null;
        }

        IncidentResponseBuilder builder = IncidentResponse.builder()
                .id(incident.getId())
                .incidentNumber(incident.getIncidentNumber())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .status(incident.getStatus())
                .priority(incident.getPriority())
                .severity(incident.getSeverity())
                .resolvedAt(incident.getResolvedAt())
                .closedAt(incident.getClosedAt())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt());

        if (incident.getCategory() != null) {
            builder.categoryId(incident.getCategory().getId())
                    .categoryName(incident.getCategory().getName())
                    .categorySlug(incident.getCategory().getSlug());
        }

        if (incident.getReporter() != null) {
            builder.reporterId(incident.getReporter().getId())
                    .reporterName(incident.getReporter().getFirstName() + " " + incident.getReporter().getLastName())
                    .reporterEmail(incident.getReporter().getEmail());
        }

        if (incident.getAssignee() != null) {
            builder.assigneeId(incident.getAssignee().getId())
                    .assigneeName(incident.getAssignee().getFirstName() + " " + incident.getAssignee().getLastName())
                    .assigneeEmail(incident.getAssignee().getEmail());
        }

        if (incident.getTeam() != null) {
            builder.teamId(incident.getTeam().getId())
                    .teamName(incident.getTeam().getName());
        }

        return builder.build();
    }
}
