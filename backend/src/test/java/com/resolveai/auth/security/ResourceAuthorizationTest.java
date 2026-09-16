package com.resolveai.auth.security;

import com.resolveai.auth.entity.Role;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentStatus;
import com.resolveai.knowledge.entity.KnowledgeArticle;
import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import com.resolveai.team.entity.Team;
import com.resolveai.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Resource-Level Authorization & Security Invariant Tests (Phase 13)")
class ResourceAuthorizationTest {

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
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
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

    @Nested
    @DisplayName("Incident Resource-Level Authorization")
    class IncidentResourceAuthorizationTests {

        @Test
        @DisplayName("Employee can only access their own reported incidents, not others")
        void employeeResourceIsolation() {
            User emp1 = buildUser(10L, "emp1@resolveai.com", RoleConstants.EMPLOYEE, null);
            authenticateAs(emp1);

            Incident ownIncident = Incident.builder()
                    .id(1L)
                    .reporter(emp1)
                    .status(IncidentStatus.NEW)
                    .build();

            User emp2 = buildUser(20L, "emp2@resolveai.com", RoleConstants.EMPLOYEE, null);
            Incident foreignIncident = Incident.builder()
                    .id(2L)
                    .reporter(emp2)
                    .status(IncidentStatus.NEW)
                    .build();

            assertThat(authorizationService.canAccessIncident(ownIncident)).isTrue();
            assertThat(authorizationService.canAccessIncident(foreignIncident)).isFalse();
        }

        @Test
        @DisplayName("Employee can update incident only if reporter AND status is NEW")
        void employeeUpdateRestrictions() {
            User emp = buildUser(10L, "emp@resolveai.com", RoleConstants.EMPLOYEE, null);
            authenticateAs(emp);

            Incident newIncident = Incident.builder().id(1L).reporter(emp).status(IncidentStatus.NEW).build();
            Incident inProgressIncident = Incident.builder().id(2L).reporter(emp).status(IncidentStatus.IN_PROGRESS).build();
            Incident resolvedIncident = Incident.builder().id(3L).reporter(emp).status(IncidentStatus.RESOLVED).build();

            assertThat(authorizationService.canUpdateIncident(newIncident)).isTrue();
            assertThat(authorizationService.canUpdateIncident(inProgressIncident)).isFalse();
            assertThat(authorizationService.canUpdateIncident(resolvedIncident)).isFalse();
        }

        @Test
        @DisplayName("Employee status transitions: only RESOLVED -> CLOSED or RESOLVED -> REOPENED allowed")
        void employeeStatusTransitions() {
            User emp = buildUser(10L, "emp@resolveai.com", RoleConstants.EMPLOYEE, null);
            authenticateAs(emp);

            Incident resolved = Incident.builder().id(1L).reporter(emp).status(IncidentStatus.RESOLVED).build();
            Incident inProgress = Incident.builder().id(2L).reporter(emp).status(IncidentStatus.IN_PROGRESS).build();

            assertThat(authorizationService.canChangeStatus(resolved, IncidentStatus.CLOSED)).isTrue();
            assertThat(authorizationService.canChangeStatus(resolved, IncidentStatus.REOPENED)).isTrue();
            assertThat(authorizationService.canChangeStatus(resolved, IncidentStatus.IN_PROGRESS)).isFalse();

            assertThat(authorizationService.canChangeStatus(inProgress, IncidentStatus.RESOLVED)).isFalse();
            assertThat(authorizationService.canChangeStatus(inProgress, IncidentStatus.CLOSED)).isFalse();
        }

        @Test
        @DisplayName("Engineer can access only if assigned, reporter, or in team")
        void engineerResourceAccess() {
            User engineer = buildUser(30L, "eng@resolveai.com", RoleConstants.ENGINEER, null);
            authenticateAs(engineer);

            User reporter = buildUser(40L, "rep@resolveai.com", RoleConstants.EMPLOYEE, null);
            Incident assignedIncident = Incident.builder().id(1L).reporter(reporter).assignee(engineer).build();
            Incident ownIncident = Incident.builder().id(2L).reporter(engineer).build();
            Incident unrelatedIncident = Incident.builder().id(3L).reporter(reporter).build();

            assertThat(authorizationService.canAccessIncident(assignedIncident)).isTrue();
            assertThat(authorizationService.canAccessIncident(ownIncident)).isTrue();
            assertThat(authorizationService.canAccessIncident(unrelatedIncident)).isFalse();
        }

        @Test
        @DisplayName("Manager can access incidents scoped to their assigned team")
        void managerTeamScoping() {
            Team platformTeam = Team.builder().id(100L).name("Platform").build();
            Team secTeam = Team.builder().id(200L).name("Security").build();

            User manager = buildUser(50L, "mgr@resolveai.com", RoleConstants.MANAGER, platformTeam);
            authenticateAs(manager);

            Incident teamIncident = Incident.builder().id(1L).team(platformTeam).build();
            Incident otherTeamIncident = Incident.builder().id(2L).team(secTeam).build();

            assertThat(authorizationService.canAccessIncident(teamIncident)).isTrue();
            assertThat(authorizationService.canAccessIncident(otherTeamIncident)).isFalse();
        }
    }

    @Nested
    @DisplayName("Knowledge Base Resource-Level Authorization")
    class KnowledgeArticleResourceAuthorizationTests {

        @Test
        @DisplayName("PUBLISHED articles are readable by any authenticated user; DRAFT/ARCHIVED are restricted")
        void knowledgeArticleVisibility() {
            User employee = buildUser(10L, "emp@resolveai.com", RoleConstants.EMPLOYEE, null);
            User authorEngineer = buildUser(20L, "author@resolveai.com", RoleConstants.ENGINEER, null);
            User otherEngineer = buildUser(30L, "other@resolveai.com", RoleConstants.ENGINEER, null);

            KnowledgeArticle published = KnowledgeArticle.builder().id(1L).author(authorEngineer).status(KnowledgeArticleStatus.PUBLISHED).build();
            KnowledgeArticle draft = KnowledgeArticle.builder().id(2L).author(authorEngineer).status(KnowledgeArticleStatus.DRAFT).build();
            KnowledgeArticle archived = KnowledgeArticle.builder().id(3L).author(authorEngineer).status(KnowledgeArticleStatus.ARCHIVED).build();

            // Employee can only view published
            authenticateAs(employee);
            assertThat(authorizationService.canViewKnowledgeArticle(published)).isTrue();
            assertThat(authorizationService.canViewKnowledgeArticle(draft)).isFalse();
            assertThat(authorizationService.canViewKnowledgeArticle(archived)).isFalse();

            // Author engineer can view draft and archived
            authenticateAs(authorEngineer);
            assertThat(authorizationService.canViewKnowledgeArticle(published)).isTrue();
            assertThat(authorizationService.canViewKnowledgeArticle(draft)).isTrue();
            assertThat(authorizationService.canViewKnowledgeArticle(archived)).isTrue();

            // Unrelated engineer cannot view other author's draft
            authenticateAs(otherEngineer);
            assertThat(authorizationService.canViewKnowledgeArticle(published)).isTrue();
            assertThat(authorizationService.canViewKnowledgeArticle(draft)).isFalse();
            assertThat(authorizationService.canViewKnowledgeArticle(archived)).isFalse();
        }

        @Test
        @DisplayName("Managing knowledge articles: Engineer can manage only own; Employee cannot manage any")
        void knowledgeArticleManagement() {
            User employee = buildUser(10L, "emp@resolveai.com", RoleConstants.EMPLOYEE, null);
            User author = buildUser(20L, "author@resolveai.com", RoleConstants.ENGINEER, null);
            User other = buildUser(30L, "other@resolveai.com", RoleConstants.ENGINEER, null);

            KnowledgeArticle article = KnowledgeArticle.builder().id(1L).author(author).status(KnowledgeArticleStatus.DRAFT).build();

            authenticateAs(employee);
            assertThat(authorizationService.canManageKnowledgeArticle(article)).isFalse();
            assertThat(authorizationService.canCreateKnowledgeArticle()).isFalse();

            authenticateAs(author);
            assertThat(authorizationService.canManageKnowledgeArticle(article)).isTrue();
            assertThat(authorizationService.canCreateKnowledgeArticle()).isTrue();

            authenticateAs(other);
            assertThat(authorizationService.canManageKnowledgeArticle(article)).isFalse();
            assertThat(authorizationService.canCreateKnowledgeArticle()).isTrue();
        }
    }
}