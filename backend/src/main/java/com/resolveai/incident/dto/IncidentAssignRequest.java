package com.resolveai.incident.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for assigning an Incident to an Engineer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentAssignRequest {

    @NotNull(message = "Engineer ID is required")
    private Long engineerId;

    private String assignmentReason;
}
