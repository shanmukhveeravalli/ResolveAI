package com.resolveai.incident.dto;

import com.resolveai.incident.entity.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for transitioning an Incident status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private IncidentStatus status;

    private String comment;
}
