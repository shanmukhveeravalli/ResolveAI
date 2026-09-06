package com.resolveai.team.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for adding a user to a team.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberRequest {

    @NotNull(message = "User ID is required")
    private Long userId;
}
