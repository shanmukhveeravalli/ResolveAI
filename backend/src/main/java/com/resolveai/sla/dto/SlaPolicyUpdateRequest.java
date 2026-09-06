package com.resolveai.sla.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for updating an existing SLA Policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaPolicyUpdateRequest {

    @Size(max = 100, message = "Policy name must not exceed 100 characters")
    private String name;

    @Min(value = 1, message = "Response time must be at least 1 minute")
    private Integer responseTimeMinutes;

    @Min(value = 1, message = "Resolution time must be at least 1 minute")
    private Integer resolutionTimeMinutes;

    @Min(value = 1, message = "Escalation threshold must be at least 1 minute")
    private Integer escalationThresholdMinutes;

    private Boolean isActive;
}
