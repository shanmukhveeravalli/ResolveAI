package com.resolveai.sla.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resolveai.incident.entity.Priority;
import com.resolveai.sla.entity.SlaPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for SLA Policy configurations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlaPolicyResponse {

    private Long id;
    private String name;
    private Priority priority;
    private Integer responseTimeMinutes;
    private Integer resolutionTimeMinutes;
    private Integer escalationThresholdMinutes;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static SlaPolicyResponse fromEntity(SlaPolicy policy) {
        if (policy == null) {
            return null;
        }

        return SlaPolicyResponse.builder()
                .id(policy.getId())
                .name(policy.getName())
                .priority(policy.getPriority())
                .responseTimeMinutes(policy.getResponseTimeMinutes())
                .resolutionTimeMinutes(policy.getResolutionTimeMinutes())
                .escalationThresholdMinutes(policy.getEscalationThresholdMinutes())
                .isActive(policy.getIsActive())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }
}
