package com.resolveai.team.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resolveai.team.entity.TeamMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for team membership.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamMemberResponse {

    private Long id;
    private Long teamId;
    private String teamName;
    private Long userId;
    private String userEmail;
    private String userName;
    private String userRole;
    private Instant joinedAt;

    public static TeamMemberResponse fromEntity(TeamMember member) {
        if (member == null) {
            return null;
        }

        TeamMemberResponseBuilder builder = TeamMemberResponse.builder()
                .id(member.getId())
                .joinedAt(member.getJoinedAt());

        if (member.getTeam() != null) {
            builder.teamId(member.getTeam().getId())
                    .teamName(member.getTeam().getName());
        }

        if (member.getUser() != null) {
            builder.userId(member.getUser().getId())
                    .userEmail(member.getUser().getEmail())
                    .userName((member.getUser().getFirstName() + " " + member.getUser().getLastName()).trim());
            if (member.getUser().getRole() != null) {
                builder.userRole(member.getUser().getRole().getName());
            }
        }

        return builder.build();
    }
}
