package com.resolveai.incident.dto;

import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.entity.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating a new Incident.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 5000, message = "Description must be between 10 and 5000 characters")
    private String description;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotNull(message = "Priority is required")
    private Priority priority;

    @NotNull(message = "Severity is required")
    private Severity severity;
}
