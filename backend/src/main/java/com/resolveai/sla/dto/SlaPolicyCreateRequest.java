package com.resolveai.sla.dto;

import com.resolveai.incident.entity.Priority;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating a new SLA Policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaPolicyCreateRequest {

    @NotBlank(message = "Policy name is required")
    @Size(max = 100, message = "Policy name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Priority is required")
    private Priority priority;

    @NotNull(message = "Response time minutes is required")
    @Min(value = 1, message = "Response time must be at least 1 minute")
    private Integer responseTimeMinutes;

    @NotNull(message = "Resolution time minutes is required")
    @Min(value = 1, message = "Resolution time must be at least 1 minute")
    private Integer resolutionTimeMinutes;

    @NotNull(message = "Escalation threshold minutes is required")
    @Min(value = 1, message = "Escalation threshold must be at least 1 minute")
    private Integer escalationThresholdMinutes;

    @Builder.Default
    private Boolean isActive = true;
}
