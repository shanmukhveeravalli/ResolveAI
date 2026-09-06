package com.resolveai.team.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resolveai.team.entity.Team;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Enterprise Team Response DTO hiding entity internals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamResponse {

    private Long id;
    private String name;
    private String description;
    private Long leadUserId;
    private String leadUserName;
    private String leadUserEmail;
    private Boolean isActive;
    private Integer memberCount;
    private Instant createdAt;
    private Instant updatedAt;

    public static TeamResponse fromEntity(Team team) {
        return fromEntity(team, 0);
    }

    public static TeamResponse fromEntity(Team team, int memberCount) {
        if (team == null) {
            return null;
        }

        TeamResponseBuilder builder = TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .isActive(team.getIsActive())
                .memberCount(memberCount)
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt());

        if (team.getLeadUser() != null) {
            builder.leadUserId(team.getLeadUser().getId())
                    .leadUserName((team.getLeadUser().getFirstName() + " " + team.getLeadUser().getLastName()).trim())
                    .leadUserEmail(team.getLeadUser().getEmail());
        }

        return builder.build();
    }
}
