package com.resolveai.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.auth.security.JwtService;
import com.resolveai.team.dto.TeamCreateRequest;
import com.resolveai.team.dto.TeamMemberRequest;
import com.resolveai.team.dto.TeamUpdateRequest;
import com.resolveai.team.entity.Team;
import com.resolveai.team.entity.TeamMember;
import com.resolveai.team.repository.TeamMemberRepository;
import com.resolveai.team.repository.TeamRepository;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive integration test suite for Phase 6 Team & Member management.
 * Verifies CRUD operations, team lead assignment, membership management,
 * and RBAC security rules (401, 403, 404, 409).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeamIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Role employeeRole;
    private Role engineerRole;
    private Role managerRole;
    private Role adminRole;

    private User employeeUser;
    private User engineerUser;
    private User managerUser;
    private User adminUser;

    private String employeeToken;
    private String engineerToken;
    private String managerToken;
    private String adminToken;

    private Team sampleTeam;

    @BeforeEach
    void setUp() {
        employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        engineerRole = roleRepository.findByName("ENGINEER").orElseThrow();
        managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        employeeUser = createTestUser("team.emp@enterprise.com", employeeRole, null, true);
        engineerUser = createTestUser("team.eng@enterprise.com", engineerRole, null, true);
        managerUser = createTestUser("team.mgr@enterprise.com", managerRole, null, true);
        adminUser = createTestUser("team.adm@enterprise.com", adminRole, null, true);

        employeeToken = jwtService.generateAccessToken(employeeUser);
        engineerToken = jwtService.generateAccessToken(engineerUser);
        managerToken = jwtService.generateAccessToken(managerUser);
        adminToken = jwtService.generateAccessToken(adminUser);

        sampleTeam = teamRepository.save(Team.builder()
                .name("Core Infrastructure Squad")
                .description("Handles Kubernetes and cloud infrastructure")
                .leadUser(engineerUser)
                .isActive(true)
                .build());
    }

    private User createTestUser(String email, Role role, Team team, boolean isActive) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName(role.getName())
                .role(role)
                .team(team)
                .isActive(isActive)
                .build());
    }

    // =========================================================================
    // 1. TEAM CREATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("1. Team Creation")
    class CreationTests {

        @Test
        @DisplayName("Manager can create team with lead user")
        void testManagerCanCreateTeam() throws Exception {
            TeamCreateRequest request = TeamCreateRequest.builder()
                    .name("Database Reliability Squad")
                    .description("L2/L3 support for PostgreSQL and Redis")
                    .leadUserId(engineerUser.getId())
                    .build();

            mockMvc.perform(post("/api/teams")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.name").value("Database Reliability Squad"))
                    .andExpect(jsonPath("$.data.leadUserId").value(engineerUser.getId()))
                    .andExpect(jsonPath("$.data.leadUserEmail").value(engineerUser.getEmail()))
                    .andExpect(jsonPath("$.data.isActive").value(true));

            assertThat(teamRepository.existsByName("Database Reliability Squad")).isTrue();
        }

        @Test
        @DisplayName("Admin can create team without lead user")
        void testAdminCanCreateTeamWithoutLead() throws Exception {
            TeamCreateRequest request = TeamCreateRequest.builder()
                    .name("Security Operations")
                    .description("SOC incident triage and threat hunting")
                    .build();

            mockMvc.perform(post("/api/teams")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.name").value("Security Operations"));
        }

        @Test
        @DisplayName("Employee cannot create team (403 Forbidden)")
        void testEmployeeCannotCreateTeam() throws Exception {
            TeamCreateRequest request = TeamCreateRequest.builder()
                    .name("Shadow IT Squad")
                    .build();

            mockMvc.perform(post("/api/teams")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Engineer cannot create team (403 Forbidden)")
        void testEngineerCannotCreateTeam() throws Exception {
            TeamCreateRequest request = TeamCreateRequest.builder()
                    .name("Engineer Squad")
                    .build();

            mockMvc.perform(post("/api/teams")
                            .header("Authorization", "Bearer " + engineerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated request is rejected (401 Unauthorized)")
        void testUnauthenticatedCreateRejected() throws Exception {
            TeamCreateRequest request = TeamCreateRequest.builder()
                    .name("Ghost Squad")
                    .build();

            mockMvc.perform(post("/api/teams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Duplicate team name returns 409 Conflict")
        void testDuplicateTeamNameRejected() throws Exception {
            TeamCreateRequest request = TeamCreateRequest.builder()
                    .name("Core Infrastructure Squad")
                    .build();

            mockMvc.perform(post("/api/teams")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CONFLICT"));
        }

        @Test
        @DisplayName("Missing team name returns 400 Bad Request")
        void testMissingNameRejected() throws Exception {
            TeamCreateRequest request = TeamCreateRequest.builder()
                    .name("")
                    .build();

            mockMvc.perform(post("/api/teams")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Assigning non-existent user as lead returns 404")
        void testAssignNonexistentLeadRejected() throws Exception {
            TeamCreateRequest request = TeamCreateRequest.builder()
                    .name("Platform Squad")
                    .leadUserId(99999L)
                    .build();

            mockMvc.perform(post("/api/teams")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Assigning inactive user as lead returns 400")
        void testAssignInactiveLeadRejected() throws Exception {
            User inactiveEng = createTestUser("inactive.eng@enterprise.com", engineerRole, null, false);
            TeamCreateRequest request = TeamCreateRequest.builder()
                    .name("Platform Squad")
                    .leadUserId(inactiveEng.getId())
                    .build();

            mockMvc.perform(post("/api/teams")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("Assigning employee role user as lead returns 400")
        void testAssignEmployeeAsLeadRejected() throws Exception {
            TeamCreateRequest request = TeamCreateRequest.builder()
                    .name("Platform Squad")
                    .leadUserId(employeeUser.getId())
                    .build();

            mockMvc.perform(post("/api/teams")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }
    }

    // =========================================================================
    // 2. TEAM RETRIEVAL & UPDATE TESTS
    // =========================================================================
    @Nested
    @DisplayName("2. Team Retrieval & Update")
    class RetrievalAndUpdateTests {

        @Test
        @DisplayName("Authenticated user can list teams")
        void testListTeams() throws Exception {
            mockMvc.perform(get("/api/teams")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[?(@.name == 'Core Infrastructure Squad')]").exists());
        }

        @Test
        @DisplayName("Authenticated user can view team by ID")
        void testGetTeamById() throws Exception {
            mockMvc.perform(get("/api/teams/" + sampleTeam.getId())
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.id").value(sampleTeam.getId()))
                    .andExpect(jsonPath("$.data.name").value("Core Infrastructure Squad"))
                    .andExpect(jsonPath("$.data.leadUserId").value(engineerUser.getId()));
        }

        @Test
        @DisplayName("Nonexistent team returns 404 Not Found")
        void testGetNonexistentTeam() throws Exception {
            mockMvc.perform(get("/api/teams/99999")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Manager can update team description and active status")
        void testManagerCanUpdateTeam() throws Exception {
            TeamUpdateRequest updateRequest = TeamUpdateRequest.builder()
                    .description("Updated description for infrastructure team")
                    .isActive(true)
                    .build();

            mockMvc.perform(put("/api/teams/" + sampleTeam.getId())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.description").value("Updated description for infrastructure team"));
        }

        @Test
        @DisplayName("Employee cannot update team (403 Forbidden)")
        void testEmployeeCannotUpdateTeam() throws Exception {
            TeamUpdateRequest updateRequest = TeamUpdateRequest.builder()
                    .name("Hacked Name")
                    .build();

            mockMvc.perform(put("/api/teams/" + sampleTeam.getId())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 3. TEAM MEMBERSHIP TESTS
    // =========================================================================
    @Nested
    @DisplayName("3. Team Membership Management")
    class MembershipTests {

        @Test
        @DisplayName("Manager can add member to team")
        void testAddMemberSuccess() throws Exception {
            User newMember = createTestUser("new.eng@enterprise.com", engineerRole, null, true);

            TeamMemberRequest request = TeamMemberRequest.builder()
                    .userId(newMember.getId())
                    .build();

            mockMvc.perform(post("/api/teams/" + sampleTeam.getId() + "/members")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.userId").value(newMember.getId()))
                    .andExpect(jsonPath("$.data.teamId").value(sampleTeam.getId()));

            assertThat(teamMemberRepository.existsByTeamIdAndUserId(sampleTeam.getId(), newMember.getId())).isTrue();
        }

        @Test
        @DisplayName("Adding duplicate member returns 409 Conflict")
        void testAddDuplicateMemberRejected() throws Exception {
            // Add member first
            teamMemberRepository.save(TeamMember.builder()
                    .team(sampleTeam)
                    .user(engineerUser)
                    .build());

            TeamMemberRequest request = TeamMemberRequest.builder()
                    .userId(engineerUser.getId())
                    .build();

            mockMvc.perform(post("/api/teams/" + sampleTeam.getId() + "/members")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CONFLICT"));
        }

        @Test
        @DisplayName("Adding inactive user to team returns 400 Bad Request")
        void testAddInactiveUserRejected() throws Exception {
            User inactiveUser = createTestUser("inactive.member@enterprise.com", engineerRole, null, false);

            TeamMemberRequest request = TeamMemberRequest.builder()
                    .userId(inactiveUser.getId())
                    .build();

            mockMvc.perform(post("/api/teams/" + sampleTeam.getId() + "/members")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("Employee cannot add team member (403 Forbidden)")
        void testEmployeeCannotAddMember() throws Exception {
            TeamMemberRequest request = TeamMemberRequest.builder()
                    .userId(engineerUser.getId())
                    .build();

            mockMvc.perform(post("/api/teams/" + sampleTeam.getId() + "/members")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Authenticated user can list team members")
        void testListTeamMembers() throws Exception {
            teamMemberRepository.save(TeamMember.builder()
                    .team(sampleTeam)
                    .user(engineerUser)
                    .build());

            mockMvc.perform(get("/api/teams/" + sampleTeam.getId() + "/members")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].userId").value(engineerUser.getId()));
        }

        @Test
        @DisplayName("Manager can remove team member")
        void testRemoveMemberSuccess() throws Exception {
            teamMemberRepository.save(TeamMember.builder()
                    .team(sampleTeam)
                    .user(engineerUser)
                    .build());

            mockMvc.perform(delete("/api/teams/" + sampleTeam.getId() + "/members/" + engineerUser.getId())
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));

            assertThat(teamMemberRepository.existsByTeamIdAndUserId(sampleTeam.getId(), engineerUser.getId())).isFalse();
            // User entity itself must not be deleted
            assertThat(userRepository.existsById(engineerUser.getId())).isTrue();
        }

        @Test
        @DisplayName("Removing non-member returns 404 Not Found")
        void testRemoveNonMemberRejected() throws Exception {
            mockMvc.perform(delete("/api/teams/" + sampleTeam.getId() + "/members/" + employeeUser.getId())
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Employee cannot remove team member (403 Forbidden)")
        void testEmployeeCannotRemoveMember() throws Exception {
            mockMvc.perform(delete("/api/teams/" + sampleTeam.getId() + "/members/" + engineerUser.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }
    }
}
