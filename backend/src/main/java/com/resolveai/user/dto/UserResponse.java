package com.resolveai.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resolveai.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Safe User Profile DTO. Never exposes passwords, hashes, or security secrets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String role;
    private Long teamId;
    private String teamName;
    private Boolean isActive;
    private Instant createdAt;

    public static UserResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String full = (first + " " + last).trim();

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(first)
                .lastName(last)
                .fullName(full.isEmpty() ? null : full)
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .teamId(user.getTeam() != null ? user.getTeam().getId() : null)
                .teamName(user.getTeam() != null ? user.getTeam().getName() : null)
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
