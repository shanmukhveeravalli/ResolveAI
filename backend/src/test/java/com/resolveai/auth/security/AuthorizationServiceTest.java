package com.resolveai.auth.security;

import com.resolveai.auth.entity.Role;
import com.resolveai.team.entity.Team;
import com.resolveai.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthorizationService Unit Tests")
class AuthorizationServiceTest {

    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationService();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        SecurityUser securityUser = new SecurityUser(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User buildUser(Long id, String email, String roleName, Team team) {
        Role role = Role.builder().name(roleName).build();
        return User.builder()
                .id(id)
                .email(email)
                .firstName("Test")
                .lastName("User")
                .role(role)
                .team(team)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Unauthenticated context returns empty user details")
    void testUnauthenticatedReturnsEmpty() {
        assertThat(authorizationService.getCurrentUser()).isEmpty();
        assertThat(authorizationService.getCurrentUserId()).isEmpty();
        assertThat(authorizationService.getCurrentUserEmail()).isEmpty();
        assertThat(authorizationService.hasRole("ADMIN")).isFalse();
        assertThat(authorizationService.hasAnyRole("ADMIN", "MANAGER")).isFalse();
        assertThat(authorizationService.isAdmin()).isFalse();
        assertThat(authorizationService.isCurrentUser(1L)).isFalse();
        assertThat(authorizationService.canAccessIncident(1L, 2L, 3L)).isFalse();
    }

    @Test
    @DisplayName("Null or blank role queries return false")
    void testBlankRoleQueriesReturnFalse() {
        User admin = buildUser(1L, "admin@resolveai.internal", RoleConstants.ADMIN, null);
        authenticateAs(admin);

        assertThat(authorizationService.hasRole(null)).isFalse();
        assertThat(authorizationService.hasRole("")).isFalse();
        assertThat(authorizationService.hasRole("   ")).isFalse();
        assertThat(authorizationService.hasAnyRole()).isFalse();
        assertThat(authorizationService.hasAnyRole((String[]) null)).isFalse();
        assertThat(authorizationService.isCurrentUser(null)).isFalse();
    }

    @Test
    @DisplayName("Admin user role checks succeed for both prefixed and unprefixed queries")
    void testAdminUserRoleChecks() {
        User admin = buildUser(1L, "admin@resolveai.internal", RoleConstants.ADMIN, null);
        authenticateAs(admin);

        assertThat(authorizationService.getCurrentUser()).isPresent();
        assertThat(authorizationService.getCurrentUserId()).contains(1L);
        assertThat(authorizationService.getCurrentUserEmail()).contains("admin@resolveai.internal");
        assertThat(authorizationService.hasRole(RoleConstants.ADMIN)).isTrue();
        assertThat(authorizationService.hasRole(RoleConstants.ROLE_ADMIN)).isTrue();
        assertThat(authorizationService.hasRole(RoleConstants.EMPLOYEE)).isFalse();
        assertThat(authorizationService.hasAnyRole(RoleConstants.EMPLOYEE, RoleConstants.ADMIN)).isTrue();
        assertThat(authorizationService.isAdmin()).isTrue();
        assertThat(authorizationService.isCurrentUser(1L)).isTrue();
        assertThat(authorizationService.isCurrentUser(999L)).isFalse();
    }

    @Test
    @DisplayName("canAccessIncident grants access to Admin for any incident")
    void testCanAccessIncidentAdmin() {
        User admin = buildUser(1L, "admin@resolveai.internal", RoleConstants.ADMIN, null);
        authenticateAs(admin);

        assertThat(authorizationService.canAccessIncident(10L, 20L, 30L)).isTrue();
    }

    @Test
    @DisplayName("canAccessIncident restricts Employee to their own reported incidents")
    void testCanAccessIncidentEmployee() {
        User employee = buildUser(100L, "emp@resolveai.internal", RoleConstants.EMPLOYEE, null);
        authenticateAs(employee);

        assertThat(authorizationService.canAccessIncident(100L, 200L, 300L)).isTrue();
        assertThat(authorizationService.canAccessIncident(101L, 200L, 300L)).isFalse();
    }

    @Test
    @DisplayName("canAccessIncident grants access to Engineer when assignee or reporter")
    void testCanAccessIncidentEngineer() {
        User engineer = buildUser(200L, "eng@resolveai.internal", RoleConstants.ENGINEER, null);
        authenticateAs(engineer);

        assertThat(authorizationService.canAccessIncident(100L, 200L, 300L)).isTrue();
        assertThat(authorizationService.canAccessIncident(200L, 999L, 300L)).isTrue();
        assertThat(authorizationService.canAccessIncident(100L, 999L, 300L)).isFalse();
    }

    @Test
    @DisplayName("canAccessIncident grants access to Manager for incidents in their team")
    void testCanAccessIncidentManager() {
        Team devOpsTeam = Team.builder().id(50L).name("DevOps").build();
        User managerWithTeam = buildUser(300L, "mgr@resolveai.internal", RoleConstants.MANAGER, devOpsTeam);
        authenticateAs(managerWithTeam);

        assertThat(authorizationService.canAccessIncident(100L, 200L, 50L)).isTrue();
        assertThat(authorizationService.canAccessIncident(100L, 200L, 99L)).isFalse();

        // Manager with null team
        User managerNoTeam = buildUser(301L, "mgr2@resolveai.internal", RoleConstants.MANAGER, null);
        authenticateAs(managerNoTeam);
        assertThat(authorizationService.canAccessIncident(100L, 200L, 50L)).isFalse();
    }
}
