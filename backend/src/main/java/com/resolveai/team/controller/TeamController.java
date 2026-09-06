package com.resolveai.team.controller;

import com.resolveai.common.dto.ApiResponse;
import com.resolveai.team.dto.*;
import com.resolveai.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing engineering and operational teams and team rosters.
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name = "Teams", description = "Team management, lead engineer assignment, and membership rosters")
@SecurityRequirement(name = "bearerAuth")
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List teams", description = "Returns active teams or all teams based on filter.")
    public ResponseEntity<ApiResponse<List<TeamResponse>>> getTeams(
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly) {
        List<TeamResponse> teams = teamService.getTeams(activeOnly);
        return ResponseEntity.ok(ApiResponse.success(teams));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get team by ID", description = "Returns detailed team attributes and member count.")
    public ResponseEntity<ApiResponse<TeamResponse>> getTeamById(@PathVariable Long id) {
        TeamResponse team = teamService.getTeamById(id);
        return ResponseEntity.ok(ApiResponse.success(team));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Create a team", description = "Creates a new team. Restricted to MANAGER and ADMIN.")
    public ResponseEntity<ApiResponse<TeamResponse>> createTeam(@Valid @RequestBody TeamCreateRequest request) {
        TeamResponse created = teamService.createTeam(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Team created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Update a team", description = "Modifies existing team attributes. Restricted to MANAGER and ADMIN.")
    public ResponseEntity<ApiResponse<TeamResponse>> updateTeam(
            @PathVariable Long id,
            @Valid @RequestBody TeamUpdateRequest request) {
        TeamResponse updated = teamService.updateTeam(id, request);
        return ResponseEntity.ok(ApiResponse.success("Team updated successfully", updated));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List team members", description = "Lists all members assigned to the given team.")
    public ResponseEntity<ApiResponse<List<TeamMemberResponse>>> getTeamMembers(@PathVariable Long id) {
        List<TeamMemberResponse> members = teamService.getTeamMembers(id);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Add member to team", description = "Adds a user to a team. Restricted to MANAGER and ADMIN.")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> addTeamMember(
            @PathVariable Long id,
            @Valid @RequestBody TeamMemberRequest request) {
        TeamMemberResponse member = teamService.addTeamMember(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Team member added successfully", member));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Remove member from team", description = "Removes a user from a team. Restricted to MANAGER and ADMIN.")
    public ResponseEntity<ApiResponse<Void>> removeTeamMember(
            @PathVariable Long id,
            @PathVariable Long userId) {
        teamService.removeTeamMember(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Team member removed successfully", null));
    }
}
