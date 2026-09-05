package com.resolveai.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.auth.security.JwtService;
import com.resolveai.incident.dto.*;
import com.resolveai.incident.entity.*;
import com.resolveai.incident.repository.CategoryRepository;
import com.resolveai.incident.repository.IncidentHistoryRepository;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.team.entity.Team;
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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IncidentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentHistoryRepository incidentHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Role employeeRole;
    private Role engineerRole;
    private Role managerRole;
    private Role adminRole;

    private User employeeUser;
    private User otherEmployeeUser;
    private User engineerUser;
    private User managerUser;
    private User adminUser;

    private String employeeToken;
    private String otherEmployeeToken;
    private String engineerToken;
    private String managerToken;
    private String adminToken;

    private Category category;
    private Team operationsTeam;

    @BeforeEach
    void setUp() {
        employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        engineerRole = roleRepository.findByName("ENGINEER").orElseThrow();
        managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        operationsTeam = teamRepository.save(Team.builder()
                .name("IT Support Ops")
                .description("Handles corporate desktop and network incidents")
                .isActive(true)
                .build());

        employeeUser = createTestUser("emp.incident@resolveai.internal", employeeRole, null);
        otherEmployeeUser = createTestUser("other.emp@resolveai.internal", employeeRole, null);
        engineerUser = createTestUser("eng.incident@resolveai.internal", engineerRole, operationsTeam);
        managerUser = createTestUser("mgr.incident@resolveai.internal", managerRole, operationsTeam);
        adminUser = createTestUser("adm.incident@resolveai.internal", adminRole, null);

        employeeToken = jwtService.generateAccessToken(employeeUser);
        otherEmployeeToken = jwtService.generateAccessToken(otherEmployeeUser);
        engineerToken = jwtService.generateAccessToken(engineerUser);
        managerToken = jwtService.generateAccessToken(managerUser);
        adminToken = jwtService.generateAccessToken(adminUser);

        category = categoryRepository.findBySlug("software-applications")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("Software Applications")
                        .slug("software-applications")
                        .isActive(true)
                        .build()));
    }

    private User createTestUser(String email, Role role, Team team) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName(role.getName())
                .role(role)
                .team(team)
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    // =========================================================================
    // 1. INCIDENT CREATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("1. Incident Creation")
    class CreationTests {

        @Test
        @DisplayName("Authenticated employee can create incident")
        void testCreateIncidentSuccess() throws Exception {
            IncidentCreateRequest request = IncidentCreateRequest.builder()
                    .title("VPN connection error on Windows 11")
                    .description("Client fails with error 809 when attempting to connect to primary gateway.")
                    .categoryId(category.getId())
                    .priority(Priority.P2)
                    .severity(Severity.HIGH)
                    .build();

            mockMvc.perform(post("/api/incidents")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.title").value("VPN connection error on Windows 11"))
                    .andExpect(jsonPath("$.data.status").value("NEW"))
                    .andExpect(jsonPath("$.data.priority").value("P2"))
                    .andExpect(jsonPath("$.data.severity").value("HIGH"))
                    .andExpect(jsonPath("$.data.reporterId").value(employeeUser.getId()))
                    .andExpect(jsonPath("$.data.incidentNumber").isNotEmpty());

            List<IncidentHistory> history = incidentHistoryRepository.findAll();
            assertThat(history).anyMatch(h -> "CREATED".equals(h.getActionType()));
        }

        @Test
        @DisplayName("Missing title is rejected with 400 Bad Request")
        void testCreateIncidentMissingTitle() throws Exception {
            IncidentCreateRequest request = IncidentCreateRequest.builder()
                    .title("")
                    .description("Description is provided but title is blank.")
                    .categoryId(category.getId())
                    .priority(Priority.P3)
                    .severity(Severity.MEDIUM)
                    .build();

            mockMvc.perform(post("/api/incidents")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Missing description is rejected with 400 Bad Request")
        void testCreateIncidentMissingDescription() throws Exception {
            IncidentCreateRequest request = IncidentCreateRequest.builder()
                    .title("Valid title here")
                    .description("")
                    .categoryId(category.getId())
                    .priority(Priority.P3)
                    .severity(Severity.MEDIUM)
                    .build();

            mockMvc.perform(post("/api/incidents")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Invalid category ID is rejected with 404 Not Found")
        void testCreateIncidentInvalidCategory() throws Exception {
            IncidentCreateRequest request = IncidentCreateRequest.builder()
                    .title("Valid title here")
                    .description("Valid description that has enough characters.")
                    .categoryId(99999L)
                    .priority(Priority.P3)
                    .severity(Severity.MEDIUM)
                    .build();

            mockMvc.perform(post("/api/incidents")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Invalid priority string in payload is rejected with 400 Bad Request")
        void testCreateIncidentInvalidPriority() throws Exception {
            String payload = """
                    {
                        "title": "Valid title for priority test",
                        "description": "Valid description with plenty of characters.",
                        "categoryId": %d,
                        "priority": "INVALID_P5",
                        "severity": "MEDIUM"
                    }
                    """.formatted(category.getId());

            mockMvc.perform(post("/api/incidents")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid severity string in payload is rejected with 400 Bad Request")
        void testCreateIncidentInvalidSeverity() throws Exception {
            String payload = """
                    {
                        "title": "Valid title for severity test",
                        "description": "Valid description with plenty of characters.",
                        "categoryId": %d,
                        "priority": "P2",
                        "severity": "EXTREME"
                    }
                    """.formatted(category.getId());

            mockMvc.perform(post("/api/incidents")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================================
    // 2. INCIDENT RETRIEVAL AND FILTERING TESTS
    // =========================================================================
    @Nested
    @DisplayName("2. Incident Retrieval and Filtering")
    class RetrievalTests {

        private Incident testIncident;

        @BeforeEach
        void createIncidentFixture() {
            testIncident = incidentRepository.save(Incident.builder()
                    .incidentNumber("INC-TEST-0001")
                    .title("Outlook client crash upon startup")
                    .description("Outlook crashes with exception 0xc0000005 immediately after opening.")
                    .status(IncidentStatus.NEW)
                    .priority(Priority.P3)
                    .severity(Severity.MEDIUM)
                    .category(category)
                    .reporter(employeeUser)
                    .build());
        }

        @Test
        @DisplayName("Reporter can view their own incident (200 OK)")
        void testGetSingleIncidentAsReporter() throws Exception {
            mockMvc.perform(get("/api/incidents/" + testIncident.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.id").value(testIncident.getId()))
                    .andExpect(jsonPath("$.data.title").value("Outlook client crash upon startup"));
        }

        @Test
        @DisplayName("Nonexistent incident returns 404 Not Found")
        void testGetNonexistentIncidentYields404() throws Exception {
            mockMvc.perform(get("/api/incidents/999999")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Unauthorized employee cannot view another employee's incident (403 Forbidden)")
        void testUnauthorizedEmployeeReceives403() throws Exception {
            mockMvc.perform(get("/api/incidents/" + testIncident.getId())
                            .header("Authorization", "Bearer " + otherEmployeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Admin can view any incident regardless of reporter (200 OK)")
        void testAdminCanViewAnyIncident() throws Exception {
            mockMvc.perform(get("/api/incidents/" + testIncident.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(testIncident.getId()));
        }

        @Test
        @DisplayName("List incidents returns paginated PageResponse")
        void testListIncidentsPaginated() throws Exception {
            mockMvc.perform(get("/api/incidents?page=0&size=10")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(10));
        }

        @Test
        @DisplayName("Employee sees only own incidents in list view")
        void testEmployeeListScoping() throws Exception {
            // Create second incident by other employee
            incidentRepository.save(Incident.builder()
                    .incidentNumber("INC-TEST-0002")
                    .title("Other employee incident")
                    .description("Detailed description of another employee ticket.")
                    .status(IncidentStatus.NEW)
                    .priority(Priority.P2)
                    .severity(Severity.HIGH)
                    .category(category)
                    .reporter(otherEmployeeUser)
                    .build());

            mockMvc.perform(get("/api/incidents")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].reporterId").value(employeeUser.getId()));
        }

        @Test
        @DisplayName("Filter incidents by priority and status")
        void testFilterIncidents() throws Exception {
            mockMvc.perform(get("/api/incidents?priority=P3&status=NEW")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }
    }

    // =========================================================================
    // 3. INCIDENT UPDATE TESTS
    // =========================================================================
    @Nested
    @DisplayName("3. Incident Update")
    class UpdateTests {

        private Incident testIncident;

        @BeforeEach
        void createIncidentFixture() {
            testIncident = incidentRepository.save(Incident.builder()
                    .incidentNumber("INC-UPD-0001")
                    .title("Original incident title")
                    .description("Original incident description before update.")
                    .status(IncidentStatus.NEW)
                    .priority(Priority.P4)
                    .severity(Severity.LOW)
                    .category(category)
                    .reporter(employeeUser)
                    .build());
        }

        @Test
        @DisplayName("Reporter can update their incident while in NEW status")
        void testAuthorizedUpdateSuccess() throws Exception {
            IncidentUpdateRequest request = IncidentUpdateRequest.builder()
                    .title("Updated incident title by reporter")
                    .description("Updated incident description with more context.")
                    .priority(Priority.P2)
                    .severity(Severity.HIGH)
                    .build();

            mockMvc.perform(put("/api/incidents/" + testIncident.getId())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("Updated incident title by reporter"))
                    .andExpect(jsonPath("$.data.priority").value("P2"));

            Incident updated = incidentRepository.findById(testIncident.getId()).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("Updated incident title by reporter");
        }

        @Test
        @DisplayName("Update with invalid title is rejected with 400 Bad Request")
        void testUpdateInvalidDataRejected() throws Exception {
            IncidentUpdateRequest request = IncidentUpdateRequest.builder()
                    .title("")
                    .description("Updated description.")
                    .build();

            mockMvc.perform(put("/api/incidents/" + testIncident.getId())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Unauthorized user cannot update incident (403 Forbidden)")
        void testUnauthorizedUpdateRejected() throws Exception {
            IncidentUpdateRequest request = IncidentUpdateRequest.builder()
                    .title("Attempted title update by hacker")
                    .description("Hacker attempting update.")
                    .build();

            mockMvc.perform(put("/api/incidents/" + testIncident.getId())
                            .header("Authorization", "Bearer " + otherEmployeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 4. INCIDENT ASSIGNMENT TESTS
    // =========================================================================
    @Nested
    @DisplayName("4. Incident Assignment")
    class AssignmentTests {

        private Incident testIncident;

        @BeforeEach
        void createIncidentFixture() {
            testIncident = incidentRepository.save(Incident.builder()
                    .incidentNumber("INC-ASN-0001")
                    .title("Incident awaiting assignment")
                    .description("Critical server unreachable on internal subnet.")
                    .status(IncidentStatus.NEW)
                    .priority(Priority.P1)
                    .severity(Severity.CRITICAL)
                    .category(category)
                    .reporter(employeeUser)
                    .build());
        }

        @Test
        @DisplayName("Manager can assign incident to active engineer (200 OK)")
        void testManagerCanAssignIncident() throws Exception {
            IncidentAssignRequest request = IncidentAssignRequest.builder()
                    .engineerId(engineerUser.getId())
                    .assignmentReason("Assigning to operations specialist for immediate triage")
                    .build();

            mockMvc.perform(patch("/api/incidents/" + testIncident.getId() + "/assign")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                    .andExpect(jsonPath("$.data.assigneeId").value(engineerUser.getId()));

            Incident assigned = incidentRepository.findById(testIncident.getId()).orElseThrow();
            assertThat(assigned.getStatus()).isEqualTo(IncidentStatus.ASSIGNED);
            assertThat(assigned.getAssignee().getId()).isEqualTo(engineerUser.getId());
        }

        @Test
        @DisplayName("Employee cannot assign incident (403 Forbidden)")
        void testEmployeeCannotAssignIncident() throws Exception {
            IncidentAssignRequest request = IncidentAssignRequest.builder()
                    .engineerId(engineerUser.getId())
                    .build();

            mockMvc.perform(patch("/api/incidents/" + testIncident.getId() + "/assign")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Assigning nonexistent engineer yields 404 Not Found")
        void testAssignNonexistentEngineerYields404() throws Exception {
            IncidentAssignRequest request = IncidentAssignRequest.builder()
                    .engineerId(999999L)
                    .build();

            mockMvc.perform(patch("/api/incidents/" + testIncident.getId() + "/assign")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Assigning non-engineer role is rejected with 400 Bad Request")
        void testAssignNonEngineerRejected() throws Exception {
            IncidentAssignRequest request = IncidentAssignRequest.builder()
                    .engineerId(employeeUser.getId()) // Employee, not engineer
                    .build();

            mockMvc.perform(patch("/api/incidents/" + testIncident.getId() + "/assign")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }
    }

    // =========================================================================
    // 5. STATUS TRANSITION LIFECYCLE TESTS
    // =========================================================================
    @Nested
    @DisplayName("5. Status Transition Lifecycle")
    class StatusLifecycleTests {

        private Incident incident;

        @BeforeEach
        void createIncidentFixture() {
            incident = incidentRepository.save(Incident.builder()
                    .incidentNumber("INC-STS-0001")
                    .title("Lifecycle state test ticket")
                    .description("Step-by-step lifecycle invariant test.")
                    .status(IncidentStatus.NEW)
                    .priority(Priority.P2)
                    .severity(Severity.HIGH)
                    .category(category)
                    .reporter(employeeUser)
                    .assignee(engineerUser)
                    .build());
        }

        @Test
        @DisplayName("Step-by-step valid transitions: NEW -> TRIAGED -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED")
        void testValidLifecycleChain() throws Exception {
            // 1. NEW -> TRIAGED
            transitionStatus(IncidentStatus.TRIAGED, adminToken, 200);

            // 2. TRIAGED -> ASSIGNED
            transitionStatus(IncidentStatus.ASSIGNED, adminToken, 200);

            // 3. ASSIGNED -> IN_PROGRESS
            transitionStatus(IncidentStatus.IN_PROGRESS, engineerToken, 200);

            // 4. IN_PROGRESS -> ESCALATED
            transitionStatus(IncidentStatus.ESCALATED, engineerToken, 200);

            // 5. ESCALATED -> IN_PROGRESS
            transitionStatus(IncidentStatus.IN_PROGRESS, engineerToken, 200);

            // 6. IN_PROGRESS -> RESOLVED
            transitionStatus(IncidentStatus.RESOLVED, engineerToken, 200);

            Incident resolved = incidentRepository.findById(incident.getId()).orElseThrow();
            assertThat(resolved.getResolvedAt()).isNotNull();

            // 7. RESOLVED -> REOPENED
            transitionStatus(IncidentStatus.REOPENED, employeeToken, 200);

            // 8. REOPENED -> IN_PROGRESS
            transitionStatus(IncidentStatus.IN_PROGRESS, engineerToken, 200);

            // 9. IN_PROGRESS -> RESOLVED
            transitionStatus(IncidentStatus.RESOLVED, engineerToken, 200);

            // 10. RESOLVED -> CLOSED
            transitionStatus(IncidentStatus.CLOSED, employeeToken, 200);

            Incident closed = incidentRepository.findById(incident.getId()).orElseThrow();
            assertThat(closed.getStatus()).isEqualTo(IncidentStatus.CLOSED);
            assertThat(closed.getClosedAt()).isNotNull();
        }

        @Test
        @DisplayName("Invalid transition NEW -> CLOSED is rejected with 400 Bad Request")
        void testInvalidTransitionNewToClosed() throws Exception {
            transitionStatus(IncidentStatus.CLOSED, adminToken, 400);
        }

        @Test
        @DisplayName("Invalid transition NEW -> RESOLVED is rejected with 400 Bad Request")
        void testInvalidTransitionNewToResolved() throws Exception {
            transitionStatus(IncidentStatus.RESOLVED, adminToken, 400);
        }

        @Test
        @DisplayName("Invalid transition CLOSED -> IN_PROGRESS is rejected with 400 Bad Request")
        void testInvalidTransitionClosedToInProgress() throws Exception {
            incident.setStatus(IncidentStatus.CLOSED);
            incidentRepository.save(incident);

            transitionStatus(IncidentStatus.IN_PROGRESS, adminToken, 400);
        }

        @Test
        @DisplayName("Employee cannot trigger engineer transitions like NEW -> TRIAGED (403 Forbidden)")
        void testEmployeeCannotTriggerEngineerTransitions() throws Exception {
            transitionStatus(IncidentStatus.TRIAGED, employeeToken, 403);
        }

        private void transitionStatus(IncidentStatus status, String token, int expectedHttp) throws Exception {
            IncidentStatusUpdateRequest req = IncidentStatusUpdateRequest.builder()
                    .status(status)
                    .comment("Automated transition test")
                    .build();

            mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().is(expectedHttp));
        }
    }

    // =========================================================================
    // 6. INCIDENT COMMENTS TESTS
    // =========================================================================
    @Nested
    @DisplayName("6. Incident Comments")
    class CommentTests {

        private Incident incident;

        @BeforeEach
        void createIncidentFixture() {
            incident = incidentRepository.save(Incident.builder()
                    .incidentNumber("INC-CMT-0001")
                    .title("Ticket for comment testing")
                    .description("Verifying customer-facing and internal investigation notes.")
                    .status(IncidentStatus.NEW)
                    .priority(Priority.P3)
                    .severity(Severity.MEDIUM)
                    .category(category)
                    .reporter(employeeUser)
                    .assignee(engineerUser)
                    .build());
        }

        @Test
        @DisplayName("Authorized user can add comment and retrieve comments")
        void testAddAndRetrieveComments() throws Exception {
            IncidentCommentCreateRequest commentReq = IncidentCommentCreateRequest.builder()
                    .commentText("Investigating system logs for root cause analysis.")
                    .isInternal(false)
                    .build();

            mockMvc.perform(post("/api/incidents/" + incident.getId() + "/comments")
                            .header("Authorization", "Bearer " + engineerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(commentReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.commentText").value("Investigating system logs for root cause analysis."))
                    .andExpect(jsonPath("$.data.authorId").value(engineerUser.getId()));

            mockMvc.perform(get("/api/incidents/" + incident.getId() + "/comments")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("Empty comment is rejected with 400 Bad Request")
        void testEmptyCommentRejected() throws Exception {
            IncidentCommentCreateRequest commentReq = IncidentCommentCreateRequest.builder()
                    .commentText("")
                    .build();

            mockMvc.perform(post("/api/incidents/" + incident.getId() + "/comments")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(commentReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Employee cannot view internal comments")
        void testInternalCommentsHiddenFromEmployee() throws Exception {
            // Engineer adds internal comment
            IncidentCommentCreateRequest internalReq = IncidentCommentCreateRequest.builder()
                    .commentText("Confidential diagnostic note: possible database corruption.")
                    .isInternal(true)
                    .build();

            mockMvc.perform(post("/api/incidents/" + incident.getId() + "/comments")
                            .header("Authorization", "Bearer " + engineerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(internalReq)))
                    .andExpect(status().isCreated());

            // Employee fetches comments - internal comment must NOT appear
            mockMvc.perform(get("/api/incidents/" + incident.getId() + "/comments")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));

            // Engineer fetches comments - internal comment is visible
            mockMvc.perform(get("/api/incidents/" + incident.getId() + "/comments")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }
    }

    // =========================================================================
    // 7. INCIDENT AUDIT HISTORY TESTS
    // =========================================================================
    @Nested
    @DisplayName("7. Incident History")
    class HistoryTests {

        @Test
        @DisplayName("Creation, assignment, and status transition record immutable audit history")
        void testAuditHistoryLifecycle() throws Exception {
            // 1. Create incident
            IncidentCreateRequest createReq = IncidentCreateRequest.builder()
                    .title("Audit test incident")
                    .description("Valid description for testing history auditing.")
                    .categoryId(category.getId())
                    .priority(Priority.P2)
                    .severity(Severity.HIGH)
                    .build();

            String response = mockMvc.perform(post("/api/incidents")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            long incidentId = objectMapper.readTree(response).path("data").path("id").asLong();

            // 2. Assign incident
            IncidentAssignRequest assignReq = IncidentAssignRequest.builder()
                    .engineerId(engineerUser.getId())
                    .assignmentReason("Assigning for triage")
                    .build();

            mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(assignReq)))
                    .andExpect(status().isOk());

            // 3. Update status to IN_PROGRESS
            IncidentStatusUpdateRequest statusReq = IncidentStatusUpdateRequest.builder()
                    .status(IncidentStatus.IN_PROGRESS)
                    .comment("Started investigation")
                    .build();

            mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                            .header("Authorization", "Bearer " + engineerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusReq)))
                    .andExpect(status().isOk());

            // 4. Retrieve history
            mockMvc.perform(get("/api/incidents/" + incidentId + "/history")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))));
        }
    }

    // =========================================================================
    // 8. SECURITY AND UNAUTHENTICATED ACCESS
    // =========================================================================
    @Nested
    @DisplayName("8. Security & Unauthenticated Access")
    class SecurityTests {

        @Test
        @DisplayName("Unauthenticated request to incident endpoint yields 401 Unauthorized")
        void testUnauthenticatedYields401() throws Exception {
            mockMvc.perform(get("/api/incidents"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

            mockMvc.perform(post("/api/incidents")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }
    }
}
