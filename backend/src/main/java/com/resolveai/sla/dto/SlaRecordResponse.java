package com.resolveai.sla.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resolveai.incident.entity.Priority;
import com.resolveai.sla.entity.SlaRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for SLA tracking records associated with incidents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlaRecordResponse {

    private Long id;
    private Long incidentId;
    private String incidentNumber;
    private Long slaPolicyId;
    private String slaPolicyName;
    private Priority priority;
    private Instant responseDueAt;
    private Instant resolutionDueAt;
    private Instant respondedAt;
    private Instant resolvedAt;
    private Boolean isResponseBreached;
    private Boolean isResolutionBreached;
    private Boolean isBreached;
    private Instant createdAt;

    public static SlaRecordResponse fromEntity(SlaRecord record) {
        if (record == null) {
            return null;
        }

        boolean breached = Boolean.TRUE.equals(record.getIsResponseBreached()) ||
                Boolean.TRUE.equals(record.getIsResolutionBreached());

        SlaRecordResponseBuilder builder = SlaRecordResponse.builder()
                .id(record.getId())
                .responseDueAt(record.getResponseDueAt())
                .resolutionDueAt(record.getResolutionDueAt())
                .respondedAt(record.getRespondedAt())
                .resolvedAt(record.getResolvedAt())
                .isResponseBreached(record.getIsResponseBreached())
                .isResolutionBreached(record.getIsResolutionBreached())
                .isBreached(breached)
                .createdAt(record.getCreatedAt());

        if (record.getIncident() != null) {
            builder.incidentId(record.getIncident().getId())
                    .incidentNumber(record.getIncident().getIncidentNumber())
                    .priority(record.getIncident().getPriority());
        }

        if (record.getSlaPolicy() != null) {
            builder.slaPolicyId(record.getSlaPolicy().getId())
                    .slaPolicyName(record.getSlaPolicy().getName());
            if (builder.priority == null) {
                builder.priority(record.getSlaPolicy().getPriority());
            }
        }

        return builder.build();
    }
}
