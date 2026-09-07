package com.resolveai.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.auth.security.JwtService;
import com.resolveai.incident.entity.Category;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentStatus;
import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.entity.Severity;
import com.resolveai.incident.repository.CategoryRepository;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.knowledge.entity.KnowledgeArticle;
import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import com.resolveai.knowledge.repository.KnowledgeArticleRepository;
import com.resolveai.sla.entity.SlaPolicy;
import com.resolveai.sla.entity.SlaRecord;
import com.resolveai.sla.repository.SlaPolicyRepository;
import com.resolveai.sla.repository.SlaRecordRepository;
import com.resolveai.team.entity.Team;
import com.resolveai.team.repository.TeamRepository;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private SlaRecordRepository slaRecordRepository;

    @Autowired
    private SlaPolicyRepository slaPolicyRepository;

    @Autowired
    private KnowledgeArticleRepository knowledgeArticleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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

    private Category category;
    private Team alphaTeam;
    private Team betaTeam;
    private SlaPolicy p1Policy;

    @BeforeEach
    void setUp() {
        employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        engineerRole = roleRepository.findByName("ENGINEER").orElseThrow();
        managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        alphaTeam = teamRepository.save(Team.builder()
                .name("Alpha Squad " + UUID.randomUUID().toString().substring(0, 6))
                .description("Primary Response Squad")
                .isActive(true)
                .build());

        betaTeam = teamRepository.save(Team.builder()
                .name("Beta Squad " + UUID.randomUUID().toString().substring(0, 6))
                .description("Secondary Squad")
                .isActive(true)
                .build());

        employeeUser = createTestUser("analytics.emp@resolveai.internal", employeeRole, null);
        engineerUser = createTestUser("analytics.eng@resolveai.internal", engineerRole, alphaTeam);
        managerUser = createTestUser("analytics.mgr@resolveai.internal", managerRole, alphaTeam);
        adminUser = createTestUser("analytics.adm@resolveai.internal", adminRole, null);

        employeeToken = jwtService.generateAccessToken(employeeUser);
        engineerToken = jwtService.generateAccessToken(engineerUser);
        managerToken = jwtService.generateAccessToken(managerUser);
        adminToken = jwtService.generateAccessToken(adminUser);

        category = categoryRepository.findBySlug("software-applications")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("Software Applications")
                        .slug("software-applications")
                        .isActive(true)
                        .build()));

        p1Policy = slaPolicyRepository.findByPriority(Priority.P1).orElseThrow();
    }

    private User createTestUser(String email, Role role, Team team) {
        return userRepository.save(User.builder()
                .email(email + UUID.randomUUID().toString().substring(0, 5))
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Analytics")
                .lastName(role.getName())
                .role(role)
                .team(team)
                .isActive(true)
                .build());
    }

    private Incident createIncident(String num, IncidentStatus status, Priority priority, Severity severity, Team team) {
        return incidentRepository.save(Incident.builder()
                .incidentNumber(num + "-" + UUID.randomUUID().toString().substring(0, 8))
                .title("Incident " + num)
                .description("Description for " + num)
                .status(status)
                .priority(priority)
                .severity(severity)
                .reporter(employeeUser)
                .assignee(engineerUser)
                .team(team)
                .category(category)
                .build());
    }

    @Nested
    @DisplayName("Security & Access Control")
    class SecurityTests {

        @Test
        @DisplayName("Unauthenticated request should return 401 Unauthorized")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/analytics/overview")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/analytics/incidents/status")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/analytics/incidents/priority")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/analytics/incidents/severity")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/analytics/teams/workload")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/analytics/sla")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/analytics/knowledge")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("EMPLOYEE role should receive 403 Forbidden")
        void employeeRole_returns403() throws Exception {
            mockMvc.perform(get("/api/analytics/overview")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/analytics/incidents/status")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/analytics/incidents/priority")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/analytics/incidents/severity")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/analytics/teams/workload")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/analytics/sla")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/analytics/knowledge")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ENGINEER role should receive 403 Forbidden")
        void engineerRole_returns403() throws Exception {
            mockMvc.perform(get("/api/analytics/overview")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/analytics/sla")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/analytics/teams/workload")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("MANAGER role should have full access (200 OK)")
        void managerRole_returns200() throws Exception {
            mockMvc.perform(get("/api/analytics/overview")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));

            mockMvc.perform(get("/api/analytics/incidents/status")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/analytics/incidents/priority")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/analytics/incidents/severity")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/analytics/teams/workload")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/analytics/sla")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/analytics/knowledge")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN role should have full access (200 OK)")
        void adminRole_returns200() throws Exception {
            mockMvc.perform(get("/api/analytics/overview")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));

            mockMvc.perform(get("/api/analytics/sla")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }
    }

    @Nested
    @DisplayName("Incident Analytics")
    class IncidentAnalyticsTests {

        @Test
        @DisplayName("Overview returns accurate aggregate counts across all statuses")
        void overview_returnsCorrectCounts() throws Exception {
            createIncident("INC-1", IncidentStatus.NEW, Priority.P1, Severity.CRITICAL, alphaTeam);
            createIncident("INC-2", IncidentStatus.TRIAGED, Priority.P2, Severity.HIGH, alphaTeam);
            createIncident("INC-3", IncidentStatus.ASSIGNED, Priority.P3, Severity.MEDIUM, alphaTeam);
            createIncident("INC-4", IncidentStatus.IN_PROGRESS, Priority.P4, Severity.LOW, alphaTeam);
            createIncident("INC-5", IncidentStatus.RESOLVED, Priority.P1, Severity.CRITICAL, alphaTeam);
            createIncident("INC-6", IncidentStatus.CLOSED, Priority.P2, Severity.HIGH, alphaTeam);
            createIncident("INC-7", IncidentStatus.ESCALATED, Priority.P1, Severity.CRITICAL, alphaTeam);
            createIncident("INC-8", IncidentStatus.REOPENED, Priority.P3, Severity.MEDIUM, alphaTeam);

            mockMvc.perform(get("/api/analytics/overview")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalIncidents").value(8))
                    .andExpect(jsonPath("$.data.openIncidents").value(4))
                    .andExpect(jsonPath("$.data.resolvedIncidents").value(1))
                    .andExpect(jsonPath("$.data.closedIncidents").value(1))
                    .andExpect(jsonPath("$.data.escalatedIncidents").value(1))
                    .andExpect(jsonPath("$.data.reopenedIncidents").value(1));
        }

        @Test
        @DisplayName("Status breakdown groups counts by IncidentStatus correctly")
        void statusBreakdown_returnsGroupedCounts() throws Exception {
            createIncident("INC-1", IncidentStatus.NEW, Priority.P1, Severity.CRITICAL, alphaTeam);
            createIncident("INC-2", IncidentStatus.NEW, Priority.P2, Severity.HIGH, alphaTeam);
            createIncident("INC-3", IncidentStatus.RESOLVED, Priority.P3, Severity.MEDIUM, alphaTeam);

            mockMvc.perform(get("/api/analytics/incidents/status")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[?(@.status == 'NEW')].count").value(hasItem(2)))
                    .andExpect(jsonPath("$.data[?(@.status == 'RESOLVED')].count").value(hasItem(1)));
        }

        @Test
        @DisplayName("Priority breakdown groups counts by Priority (P1, P2, P3, P4)")
        void priorityBreakdown_returnsGroupedCounts() throws Exception {
            createIncident("INC-1", IncidentStatus.NEW, Priority.P1, Severity.CRITICAL, alphaTeam);
            createIncident("INC-2", IncidentStatus.NEW, Priority.P1, Severity.HIGH, alphaTeam);
            createIncident("INC-3", IncidentStatus.ASSIGNED, Priority.P2, Severity.MEDIUM, alphaTeam);
            createIncident("INC-4", IncidentStatus.RESOLVED, Priority.P4, Severity.LOW, alphaTeam);

            mockMvc.perform(get("/api/analytics/incidents/priority")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(3)))
                    .andExpect(jsonPath("$.data[?(@.priority == 'P1')].count").value(hasItem(2)))
                    .andExpect(jsonPath("$.data[?(@.priority == 'P2')].count").value(hasItem(1)))
                    .andExpect(jsonPath("$.data[?(@.priority == 'P4')].count").value(hasItem(1)));
        }

        @Test
        @DisplayName("Severity breakdown groups counts by Severity (CRITICAL, HIGH, MEDIUM, LOW)")
        void severityBreakdown_returnsGroupedCounts() throws Exception {
            createIncident("INC-1", IncidentStatus.NEW, Priority.P1, Severity.CRITICAL, alphaTeam);
            createIncident("INC-2", IncidentStatus.NEW, Priority.P1, Severity.CRITICAL, alphaTeam);
            createIncident("INC-3", IncidentStatus.NEW, Priority.P2, Severity.HIGH, alphaTeam);
            createIncident("INC-4", IncidentStatus.NEW, Priority.P3, Severity.LOW, alphaTeam);

            mockMvc.perform(get("/api/analytics/incidents/severity")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(3)))
                    .andExpect(jsonPath("$.data[?(@.severity == 'CRITICAL')].count").value(hasItem(2)))
                    .andExpect(jsonPath("$.data[?(@.severity == 'HIGH')].count").value(hasItem(1)))
                    .andExpect(jsonPath("$.data[?(@.severity == 'LOW')].count").value(hasItem(1)));
        }
    }

    @Nested
    @DisplayName("Team Workload Analytics")
    class TeamWorkloadTests {

        @Test
        @DisplayName("Team workload returns open incident counts per team and 0 for idle teams")
        void teamWorkload_returnsWorkload() throws Exception {
            // Alpha team has 2 open incidents (NEW, IN_PROGRESS) and 1 closed incident (not counted)
            createIncident("INC-1", IncidentStatus.NEW, Priority.P1, Severity.CRITICAL, alphaTeam);
            createIncident("INC-2", IncidentStatus.IN_PROGRESS, Priority.P2, Severity.HIGH, alphaTeam);
            createIncident("INC-3", IncidentStatus.CLOSED, Priority.P3, Severity.MEDIUM, alphaTeam);

            // Beta team has 0 incidents

            mockMvc.perform(get("/api/analytics/teams/workload")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.teamId == " + alphaTeam.getId() + ")].assignedOpenIncidentCount").value(hasItem(2)))
                    .andExpect(jsonPath("$.data[?(@.teamId == " + betaTeam.getId() + ")].assignedOpenIncidentCount").value(hasItem(0)));
        }
    }

    @Nested
    @DisplayName("SLA Analytics")
    class SlaAnalyticsTests {

        @Test
        @DisplayName("SLA metrics returns accurate counts of total, breached response, resolution, and total breached")
        void slaMetrics_returnsCorrectCounts() throws Exception {
            Incident inc1 = createIncident("INC-SLA1", IncidentStatus.ASSIGNED, Priority.P1, Severity.CRITICAL, alphaTeam);
            Incident inc2 = createIncident("INC-SLA2", IncidentStatus.ASSIGNED, Priority.P1, Severity.CRITICAL, alphaTeam);
            Incident inc3 = createIncident("INC-SLA3", IncidentStatus.ASSIGNED, Priority.P1, Severity.CRITICAL, alphaTeam);
            Incident inc4 = createIncident("INC-SLA4", IncidentStatus.ASSIGNED, Priority.P1, Severity.CRITICAL, alphaTeam);

            Instant now = Instant.now();

            // Record 1: Response breached only
            slaRecordRepository.save(SlaRecord.builder()
                    .incident(inc1)
                    .slaPolicy(p1Policy)
                    .responseDueAt(now.minus(1, ChronoUnit.HOURS))
                    .resolutionDueAt(now.plus(1, ChronoUnit.HOURS))
                    .isResponseBreached(true)
                    .isResolutionBreached(false)
                    .build());

            // Record 2: Resolution breached only
            slaRecordRepository.save(SlaRecord.builder()
                    .incident(inc2)
                    .slaPolicy(p1Policy)
                    .responseDueAt(now.plus(1, ChronoUnit.HOURS))
                    .resolutionDueAt(now.minus(1, ChronoUnit.HOURS))
                    .isResponseBreached(false)
                    .isResolutionBreached(true)
                    .build());

            // Record 3: Both breached
            slaRecordRepository.save(SlaRecord.builder()
                    .incident(inc3)
                    .slaPolicy(p1Policy)
                    .responseDueAt(now.minus(2, ChronoUnit.HOURS))
                    .resolutionDueAt(now.minus(1, ChronoUnit.HOURS))
                    .isResponseBreached(true)
                    .isResolutionBreached(true)
                    .build());

            // Record 4: Neither breached (compliant)
            slaRecordRepository.save(SlaRecord.builder()
                    .incident(inc4)
                    .slaPolicy(p1Policy)
                    .responseDueAt(now.plus(1, ChronoUnit.HOURS))
                    .resolutionDueAt(now.plus(2, ChronoUnit.HOURS))
                    .isResponseBreached(false)
                    .isResolutionBreached(false)
                    .build());

            mockMvc.perform(get("/api/analytics/sla")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalSlaRecords").value(4))
                    .andExpect(jsonPath("$.data.responseBreaches").value(2))
                    .andExpect(jsonPath("$.data.resolutionBreaches").value(2))
                    .andExpect(jsonPath("$.data.totalBreachedRecords").value(3));
        }
    }

    @Nested
    @DisplayName("Knowledge Base Analytics")
    class KnowledgeAnalyticsTests {

        @Test
        @DisplayName("Knowledge metrics returns accurate counts of total, published, draft, and archived articles")
        void knowledgeMetrics_returnsCorrectCounts() throws Exception {
            createArticle("Article 1", KnowledgeArticleStatus.PUBLISHED);
            createArticle("Article 2", KnowledgeArticleStatus.PUBLISHED);
            createArticle("Article 3", KnowledgeArticleStatus.DRAFT);
            createArticle("Article 4", KnowledgeArticleStatus.ARCHIVED);

            mockMvc.perform(get("/api/analytics/knowledge")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalArticles").value(4))
                    .andExpect(jsonPath("$.data.publishedArticles").value(2))
                    .andExpect(jsonPath("$.data.draftArticles").value(1))
                    .andExpect(jsonPath("$.data.archivedArticles").value(1));
        }

        private KnowledgeArticle createArticle(String title, KnowledgeArticleStatus status) {
            return knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title(title)
                    .slug(title.toLowerCase().replace(" ", "-") + "-" + UUID.randomUUID().toString().substring(0, 5))
                    .problem("Problem description")
                    .symptoms("Symptoms")
                    .resolution("Resolution steps")
                    .category(category)
                    .author(managerUser)
                    .status(status)
                    .build());
        }
    }

    @Nested
    @DisplayName("Empty Data Scenario")
    class EmptyDataTests {

        @Test
        @DisplayName("All analytics endpoints handle empty database gracefully without 500 errors")
        void emptyDatabase_returnsZeroesGracefully() throws Exception {
            // In a clean test transaction with no incidents/sla records/articles created:
            mockMvc.perform(get("/api/analytics/overview")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalIncidents").value(0))
                    .andExpect(jsonPath("$.data.openIncidents").value(0))
                    .andExpect(jsonPath("$.data.resolvedIncidents").value(0))
                    .andExpect(jsonPath("$.data.closedIncidents").value(0))
                    .andExpect(jsonPath("$.data.escalatedIncidents").value(0))
                    .andExpect(jsonPath("$.data.reopenedIncidents").value(0));

            mockMvc.perform(get("/api/analytics/incidents/status")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));

            mockMvc.perform(get("/api/analytics/incidents/priority")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));

            mockMvc.perform(get("/api/analytics/incidents/severity")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));

            mockMvc.perform(get("/api/analytics/sla")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalSlaRecords").value(0))
                    .andExpect(jsonPath("$.data.responseBreaches").value(0))
                    .andExpect(jsonPath("$.data.resolutionBreaches").value(0))
                    .andExpect(jsonPath("$.data.totalBreachedRecords").value(0));

            mockMvc.perform(get("/api/analytics/knowledge")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalArticles").value(0))
                    .andExpect(jsonPath("$.data.publishedArticles").value(0))
                    .andExpect(jsonPath("$.data.draftArticles").value(0))
                    .andExpect(jsonPath("$.data.archivedArticles").value(0));
        }
    }

    @Nested
    @DisplayName("Date Range Filtering")
    class DateFilteringTests {

        @Test
        @DisplayName("Date filtering correctly filters incidents based on creation date")
        void dateFiltering_filtersByCreatedAt() throws Exception {
            Incident inc1 = createIncident("INC-OLD", IncidentStatus.NEW, Priority.P1, Severity.CRITICAL, alphaTeam);
            Incident inc2 = createIncident("INC-MID", IncidentStatus.NEW, Priority.P2, Severity.HIGH, alphaTeam);

            Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
            Instant t1 = Instant.parse("2026-06-01T00:00:00Z");
            Instant t2 = Instant.parse("2026-08-01T00:00:00Z");

            // Update createdAt timestamps directly via JPQL
            entityManager.createQuery("UPDATE Incident i SET i.createdAt = :ts WHERE i.id = :id")
                    .setParameter("ts", t0)
                    .setParameter("id", inc1.getId())
                    .executeUpdate();

            entityManager.createQuery("UPDATE Incident i SET i.createdAt = :ts WHERE i.id = :id")
                    .setParameter("ts", t2)
                    .setParameter("id", inc2.getId())
                    .executeUpdate();

            entityManager.flush();
            entityManager.clear();

            // Filter for window around inc2 using ISO-8601 UTC string (t1 to future)
            mockMvc.perform(get("/api/analytics/overview")
                            .param("from", t1.toString())
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalIncidents").value(1));

            // Filter for window around inc1 (up to t1)
            mockMvc.perform(get("/api/analytics/overview")
                            .param("to", t1.toString())
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalIncidents").value(1));

            // Filter using date string format YYYY-MM-DD (e.g. 2026-01-01 to 2026-09-01)
            mockMvc.perform(get("/api/analytics/overview")
                            .param("from", "2026-01-01")
                            .param("to", "2026-09-01")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalIncidents").value(2));

            // Filter for out-of-bound window
            mockMvc.perform(get("/api/analytics/overview")
                            .param("from", "2025-01-01")
                            .param("to", "2025-06-01")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalIncidents").value(0));

            // Test date filtering on status breakdown endpoint
            mockMvc.perform(get("/api/analytics/incidents/status")
                            .param("from", "2026-07-01")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));

            // Test invalid date format returns 400 Bad Request
            mockMvc.perform(get("/api/analytics/overview")
                            .param("from", "invalid-date")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isBadRequest());
        }
    }
}
