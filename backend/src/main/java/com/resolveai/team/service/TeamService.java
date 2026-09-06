package com.resolveai.team.service;

import com.resolveai.auth.security.RoleConstants;
import com.resolveai.common.exception.DuplicateResourceException;
import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.team.dto.*;
import com.resolveai.team.entity.Team;
import com.resolveai.team.entity.TeamMember;
import com.resolveai.team.repository.TeamMemberRepository;
import com.resolveai.team.repository.TeamRepository;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing Enterprise Teams, lead engineer assignment, and membership rosters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new team with unique name validation and optional lead user assignment.
     */
    @Transactional
    public TeamResponse createTeam(TeamCreateRequest request) {
        String trimmedName = request.getName().trim();
        if (teamRepository.existsByName(trimmedName)) {
            throw new DuplicateResourceException("Team", "name", trimmedName);
        }

        User leadUser = null;
        if (request.getLeadUserId() != null) {
            leadUser = validateAndGetLeadUser(request.getLeadUserId());
        }

        Team team = Team.builder()
                .name(trimmedName)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .leadUser(leadUser)
                .isActive(true)
                .build();

        Team saved = teamRepository.save(team);
        log.info("Team created with ID {} and name '{}'", saved.getId(), saved.getName());

        return TeamResponse.fromEntity(saved, 0);
    }

    /**
     * Updates an existing team's name, description, lead user, or active state.
     */
    @Transactional
    public TeamResponse updateTeam(Long id, TeamUpdateRequest request) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team", id));

        if (request.getName() != null && !request.getName().isBlank()) {
            String newName = request.getName().trim();
            if (!newName.equalsIgnoreCase(team.getName()) && teamRepository.existsByName(newName)) {
                throw new DuplicateResourceException("Team", "name", newName);
            }
            team.setName(newName);
        }

        if (request.getDescription() != null) {
            team.setDescription(request.getDescription().trim());
        }

        if (request.getLeadUserId() != null) {
            User leadUser = validateAndGetLeadUser(request.getLeadUserId());
            team.setLeadUser(leadUser);
        }

        if (request.getIsActive() != null) {
            team.setIsActive(request.getIsActive());
        }

        Team saved = teamRepository.save(team);
        int memberCount = teamMemberRepository.findByTeamId(id).size();
        log.info("Team updated with ID {}", saved.getId());

        return TeamResponse.fromEntity(saved, memberCount);
    }

    /**
     * Retrieves a single team by ID including its current member count.
     */
    @Transactional(readOnly = true)
    public TeamResponse getTeamById(Long id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team", id));
        int memberCount = teamMemberRepository.findByTeamId(id).size();
        return TeamResponse.fromEntity(team, memberCount);
    }

    /**
     * Lists teams, optionally filtering to active teams only.
     */
    @Transactional(readOnly = true)
    public List<TeamResponse> getTeams(Boolean activeOnly) {
        List<Team> teams = (activeOnly != null && activeOnly)
                ? teamRepository.findByIsActiveTrue()
                : teamRepository.findAll();

        return teams.stream()
                .map(team -> {
                    int memberCount = teamMemberRepository.findByTeamId(team.getId()).size();
                    return TeamResponse.fromEntity(team, memberCount);
                })
                .toList();
    }

    /**
     * Lists all members belonging to a team.
     */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getTeamMembers(Long teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new ResourceNotFoundException("Team", teamId);
        }
        return teamMemberRepository.findByTeamId(teamId).stream()
                .map(TeamMemberResponse::fromEntity)
                .toList();
    }

    /**
     * Adds a user to a team, validating active status and preventing duplicate memberships.
     */
    @Transactional
    public TeamMemberResponse addTeamMember(Long teamId, TeamMemberRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new IllegalArgumentException("Cannot add inactive user to a team");
        }

        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new DuplicateResourceException(
                    String.format("User ID %s is already a member of team '%s'", user.getId(), team.getName()));
        }

        TeamMember member = TeamMember.builder()
                .team(team)
                .user(user)
                .build();

        TeamMember saved = teamMemberRepository.save(member);

        // Sync user team association
        if (user.getTeam() == null || !team.getId().equals(user.getTeam().getId())) {
            user.setTeam(team);
            userRepository.save(user);
        }

        log.info("User {} added to team {} (team_member ID: {})", user.getEmail(), team.getName(), saved.getId());
        return TeamMemberResponse.fromEntity(saved);
    }

    /**
     * Removes a user from a team. User record remains intact.
     */
    @Transactional
    public void removeTeamMember(Long teamId, Long userId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Membership not found for user ID %d in team ID %d", userId, teamId)));

        teamMemberRepository.delete(member);

        // Disassociate user's primary team if pointing to this team
        if (user.getTeam() != null && teamId.equals(user.getTeam().getId())) {
            user.setTeam(null);
            userRepository.save(user);
        }

        // If user was team lead, unassign lead
        if (team.getLeadUser() != null && userId.equals(team.getLeadUser().getId())) {
            team.setLeadUser(null);
            teamRepository.save(team);
        }

        log.info("User ID {} removed from team ID {}", userId, teamId);
    }

    private User validateAndGetLeadUser(Long leadUserId) {
        User user = userRepository.findById(leadUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", leadUserId));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new IllegalArgumentException("Cannot assign inactive user as team lead");
        }

        String roleName = user.getRole() != null ? user.getRole().getName() : "";
        if (RoleConstants.EMPLOYEE.equalsIgnoreCase(roleName)) {
            throw new IllegalArgumentException("User with role EMPLOYEE cannot be assigned as team lead. Must be ENGINEER, MANAGER, or ADMIN.");
        }

        return user;
    }
}
