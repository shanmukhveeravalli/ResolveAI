package com.resolveai.sla;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.auth.security.JwtService;
import com.resolveai.incident.dto.IncidentAssignRequest;
import com.resolveai.incident.dto.IncidentCreateRequest;
import com.resolveai.incident.dto.IncidentStatusUpdateRequest;
import com.resolveai.incident.entity.Category;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentStatus;
import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.entity.Severity;
import com.resolveai.incident.repository.CategoryRepository;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.incident.service.IncidentService;
import com.resolveai.sla.dto.SlaPolicyCreateRequest;
import com.resolveai.sla.dto.SlaPolicyUpdateRequest;
import com.resolveai.sla.entity.SlaPolicy;
import com.resolveai.sla.entity.SlaRecord;
import com.resolveai.sla.repository.SlaPolicyRepository;
import com.resolveai.sla.repository.SlaRecordRepository;
import com.resolveai.sla.service.SlaService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive integration test suite for Phase 6 SLA Module & Incident Core SLA Integration.
 * Verifies policy CRUD, deadline calculations, response/resolution compliance & breach detection,
 * and security rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SlaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SlaPolicyRepository slaPolicyRepository;

    @Autowired
    private SlaRecordRepository slaRecordRepository;

    @Autowired
    private SlaService slaService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentService incidentService;

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
    private SlaPolicy p1Policy;

    @BeforeEach
    void setUp() {
        employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        engineerRole = roleRepository.findByName("ENGINEER").orElseThrow();
        managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        employeeUser = createTestUser("sla.emp@enterprise.com", employeeRole);
        otherEmployeeUser = createTestUser("sla.other@enterprise.com", employeeRole);
        engineerUser = createTestUser("sla.eng@enterprise.com", engineerRole);
        managerUser = createTestUser("sla.mgr@enterprise.com", managerRole);
        adminUser = createTestUser("sla.adm@enterprise.com", adminRole);

        employeeToken = jwtService.generateAccessToken(employeeUser);
        otherEmployeeToken = jwtService.generateAccessToken(otherEmployeeUser);
        engineerToken = jwtService.generateAccessToken(engineerUser);
        managerToken = jwtService.generateAccessToken(managerUser);
        adminToken = jwtService.generateAccessToken(adminUser);

        category = categoryRepository.findBySlug("network-connectivity")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("Network Connectivity")
                        .slug("network-connectivity")
                        .isActive(true)
                        .build()));

        p1Policy = slaPolicyRepository.findByPriority(Priority.P1).orElseThrow();
    }

    private User createTestUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName(role.getName())
                .role(role)
                .isActive(true)
                .build());
    }

    // =========================================================================
    // 1. SLA POLICY MANAGEMENT TESTS
    // =========================================================================
    @Nested
    @DisplayName("1. SLA Policy Management")
    class PolicyTests {

        @Test
        @DisplayName("Authenticated user can list SLA policies")
        void testListSlaPolicies() throws Exception {
            mockMvc.perform(get("/api/sla/policies")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[?(@.priority == 'P1')]").exists());
        }

        @Test
        @DisplayName("Authenticated user can view SLA policy by ID")
        void testGetPolicyById() throws Exception {
            mockMvc.perform(get("/api/sla/policies/" + p1Policy.getId())
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.id").value(p1Policy.getId()))
                    .andExpect(jsonPath("$.data.priority").value("P1"))
                    .andExpect(jsonPath("$.data.responseTimeMinutes").value(p1Policy.getResponseTimeMinutes()));
        }

        @Test
        @DisplayName("Nonexistent policy returns 404 Not Found")
        void testGetNonexistentPolicy() throws Exception {
            mockMvc.perform(get("/api/sla/policies/99999")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Duplicate policy priority returns 409 Conflict")
        void testDuplicatePolicyPriorityRejected() throws Exception {
            SlaPolicyCreateRequest request = SlaPolicyCreateRequest.builder()
                    .name("Duplicate P1")
                    .priority(Priority.P1)
                    .responseTimeMinutes(10)
                    .resolutionTimeMinutes(60)
                    .escalationThresholdMinutes(30)
                    .build();

            mockMvc.perform(post("/api/sla/policies")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CONFLICT"));
        }

        @Test
        @DisplayName("Resolution time less than response time returns 400 Bad Request")
        void testInvalidResolutionTimeRejected() throws Exception {
            // Delete P4 to test creating a valid priority with invalid targets
            SlaPolicy p4 = slaPolicyRepository.findByPriority(Priority.P4).orElse(null);
            if (p4 != null) {
                slaPolicyRepository.delete(p4);
            }

            SlaPolicyCreateRequest request = SlaPolicyCreateRequest.builder()
                    .name("Invalid Targets Policy")
                    .priority(Priority.P4)
                    .responseTimeMinutes(120)
                    .resolutionTimeMinutes(60) // Less than response time!
                    .escalationThresholdMinutes(30)
                    .build();

            mockMvc.perform(post("/api/sla/policies")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("Employee cannot create SLA policy (403 Forbidden)")
        void testEmployeeCannotCreatePolicy() throws Exception {
            SlaPolicyCreateRequest request = SlaPolicyCreateRequest.builder()
                    .name("Employee Custom SLA")
                    .priority(Priority.P3)
                    .responseTimeMinutes(10)
                    .resolutionTimeMinutes(60)
                    .escalationThresholdMinutes(30)
                    .build();

            mockMvc.perform(post("/api/sla/policies")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Manager can update policy targets")
        void testManagerCanUpdatePolicy() throws Exception {
            SlaPolicyUpdateRequest request = SlaPolicyUpdateRequest.builder()
                    .responseTimeMinutes(20)
                    .resolutionTimeMinutes(150)
                    .build();

            mockMvc.perform(put("/api/sla/policies/" + p1Policy.getId())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.responseTimeMinutes").value(20))
                    .andExpect(jsonPath("$.data.resolutionTimeMinutes").value(150));
        }

        @Test
        @DisplayName("Employee cannot update policy (403 Forbidden)")
        void testEmployeeCannotUpdatePolicy() throws Exception {
            SlaPolicyUpdateRequest request = SlaPolicyUpdateRequest.builder()
                    .responseTimeMinutes(999)
                    .build();

            mockMvc.perform(put("/api/sla/policies/" + p1Policy.getId())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 2. INCIDENT & SLA INTEGRATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("2. Incident & SLA Integration")
    class IntegrationTests {

        @Test
        @DisplayName("Creating an incident automatically generates and attaches SLA record with correct deadlines")
        void testIncidentCreationCreatesSlaRecord() throws Exception {
            IncidentCreateRequest request = IncidentCreateRequest.builder()
                    .title("BGP Routing Engine crash in Data Center A")
                    .description("Core switch routing engine failed causing partial packet loss.")
                    .categoryId(category.getId())
                    .priority(Priority.P1)
                    .severity(Severity.CRITICAL)
                    .build();

            String responseContent = mockMvc.perform(post("/api/incidents")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.sla").exists())
                    .andExpect(jsonPath("$.data.sla.priority").value("P1"))
                    .andExpect(jsonPath("$.data.sla.isResponseBreached").value(false))
                    .andExpect(jsonPath("$.data.sla.isResolutionBreached").value(false))
                    .andReturn().getResponse().getContentAsString();

            Long incidentId = objectMapper.readTree(responseContent).get("data").get("id").asLong();

            Optional<SlaRecord> recordOpt = slaRecordRepository.findByIncidentId(incidentId);
            assertThat(recordOpt).isPresent();
            SlaRecord record = recordOpt.get();

            // Verify response deadline: createdAt + 15 min (P1 default)
            Instant expectedResponseDue = record.getIncident().getCreatedAt().plus(Duration.ofMinutes(15));
            Instant expectedResolutionDue = record.getIncident().getCreatedAt().plus(Duration.ofMinutes(120));

            assertThat(record.getResponseDueAt()).isEqualTo(expectedResponseDue);
            assertThat(record.getResolutionDueAt()).isEqualTo(expectedResolutionDue);
        }

        @Test
        @DisplayName("Assigning an incident stamps SLA response timestamp")
        void testIncidentAssignmentStampsResponseSla() throws Exception {
            var incidentResp = incidentService.createIncident(IncidentCreateRequest.builder()
                    .title("Firewall ruleset sync error")
                    .description("Secondary firewall failed to pull updated access lists.")
                    .categoryId(category.getId())
                    .priority(Priority.P2)
                    .severity(Severity.HIGH)
                    .build(), employeeUser.getEmail());

            IncidentAssignRequest assignRequest = IncidentAssignRequest.builder()
                    .engineerId(engineerUser.getId())
                    .assignmentReason("Assigned to network engineer for resolution")
                    .build();

            mockMvc.perform(patch("/api/incidents/" + incidentResp.getId() + "/assign")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(assignRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.sla.respondedAt").isNotEmpty())
                    .andExpect(jsonPath("$.data.sla.isResponseBreached").value(false));

            SlaRecord record = slaRecordRepository.findByIncidentId(incidentResp.getId()).orElseThrow();
            assertThat(record.getRespondedAt()).isNotNull();
            assertThat(record.getIsResponseBreached()).isFalse();
        }

        @Test
        @DisplayName("Transitioning status to RESOLVED stamps SLA resolution timestamp and evaluates compliance")
        void testIncidentResolutionStampsResolutionSla() throws Exception {
            var incidentResp = incidentService.createIncident(IncidentCreateRequest.builder()
                    .title("VPN Gateway overload")
                    .description("Gateway memory threshold exceeded.")
                    .categoryId(category.getId())
                    .priority(Priority.P3)
                    .severity(Severity.MEDIUM)
                    .build(), employeeUser.getEmail());

            // Advance status via authenticated requests: NEW -> ASSIGNED -> IN_PROGRESS -> RESOLVED
            assignIncident(incidentResp.getId(), engineerUser.getId(), managerToken);
            transitionStatus(incidentResp.getId(), IncidentStatus.IN_PROGRESS, engineerToken);

            mockMvc.perform(patch("/api/incidents/" + incidentResp.getId() + "/status")
                            .header("Authorization", "Bearer " + engineerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(IncidentStatusUpdateRequest.builder()
                                    .status(IncidentStatus.RESOLVED)
                                    .comment("Restarted VPN daemon and balanced tunnels.")
                                    .build())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.data.sla.resolvedAt").isNotEmpty())
                    .andExpect(jsonPath("$.data.sla.isResolutionBreached").value(false));

            SlaRecord record = slaRecordRepository.findByIncidentId(incidentResp.getId()).orElseThrow();
            assertThat(record.getResolvedAt()).isNotNull();
            assertThat(record.getIsResolutionBreached()).isFalse();
        }

        @Test
        @DisplayName("Response breach is detected when response timestamp exceeds deadline")
        void testResponseBreachDetected() {
            var incidentResp = incidentService.createIncident(IncidentCreateRequest.builder()
                    .title("P1 Major Outage")
                    .description("All systems down.")
                    .categoryId(category.getId())
                    .priority(Priority.P1)
                    .severity(Severity.CRITICAL)
                    .build(), employeeUser.getEmail());

            Incident incident = incidentRepository.findById(incidentResp.getId()).orElseThrow();
            SlaRecord record = slaRecordRepository.findByIncidentId(incident.getId()).orElseThrow();

            // Simulate late response 1 hour later (P1 target is 15 mins)
            Instant lateTime = record.getResponseDueAt().plus(Duration.ofMinutes(45));
            slaService.recordResponse(incident, lateTime);

            SlaRecord updatedRecord = slaRecordRepository.findByIncidentId(incident.getId()).orElseThrow();
            assertThat(updatedRecord.getIsResponseBreached()).isTrue();
        }

        @Test
        @DisplayName("Resolution breach is detected when resolution timestamp exceeds deadline")
        void testResolutionBreachDetected() {
            var incidentResp = incidentService.createIncident(IncidentCreateRequest.builder()
                    .title("P1 Resolution Breach Test")
                    .description("Test breach logic.")
                    .categoryId(category.getId())
                    .priority(Priority.P1)
                    .severity(Severity.CRITICAL)
                    .build(), employeeUser.getEmail());

            Incident incident = incidentRepository.findById(incidentResp.getId()).orElseThrow();
            SlaRecord record = slaRecordRepository.findByIncidentId(incident.getId()).orElseThrow();

            // Simulate late resolution 5 hours later (P1 target is 2 hours)
            Instant lateTime = record.getResolutionDueAt().plus(Duration.ofHours(3));
            slaService.recordResolution(incident, lateTime);

            SlaRecord updatedRecord = slaRecordRepository.findByIncidentId(incident.getId()).orElseThrow();
            assertThat(updatedRecord.getIsResolutionBreached()).isTrue();
        }

        @Test
        @DisplayName("Reopening incident clears resolution timestamp")
        void testReopenClearsResolvedAt() throws Exception {
            var incidentResp = incidentService.createIncident(IncidentCreateRequest.builder()
                    .title("Reopen SLA test")
                    .description("Incident testing reopen SLA resets.")
                    .categoryId(category.getId())
                    .priority(Priority.P3)
                    .severity(Severity.MEDIUM)
                    .build(), employeeUser.getEmail());

            assignIncident(incidentResp.getId(), engineerUser.getId(), managerToken);
            transitionStatus(incidentResp.getId(), IncidentStatus.IN_PROGRESS, engineerToken);
            transitionStatus(incidentResp.getId(), IncidentStatus.RESOLVED, engineerToken);

            SlaRecord resolvedRecord = slaRecordRepository.findByIncidentId(incidentResp.getId()).orElseThrow();
            assertThat(resolvedRecord.getResolvedAt()).isNotNull();

            // Reporter reopens
            transitionStatus(incidentResp.getId(), IncidentStatus.REOPENED, employeeToken);

            SlaRecord reopenedRecord = slaRecordRepository.findByIncidentId(incidentResp.getId()).orElseThrow();
            assertThat(reopenedRecord.getResolvedAt()).isNull();
        }

        private void transitionStatus(Long incidentId, IncidentStatus target, String token) throws Exception {
            mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(IncidentStatusUpdateRequest.builder()
                                    .status(target)
                                    .comment("Transition to " + target)
                                    .build())))
                    .andExpect(status().isOk());
        }

        private void assignIncident(Long incidentId, Long engineerId, String token) throws Exception {
            mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(IncidentAssignRequest.builder()
                                    .engineerId(engineerId)
                                    .assignmentReason("Assigned for SLA test")
                                    .build())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Authenticated user can view incident SLA record via GET /api/sla/records/incident/{id}")
        void testGetIncidentSlaRecord() throws Exception {
            var incidentResp = incidentService.createIncident(IncidentCreateRequest.builder()
                    .title("Inspection SLA test")
                    .description("Test SLA record query endpoint.")
                    .categoryId(category.getId())
                    .priority(Priority.P2)
                    .severity(Severity.HIGH)
                    .build(), employeeUser.getEmail());

            mockMvc.perform(get("/api/sla/records/incident/" + incidentResp.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.incidentId").value(incidentResp.getId()))
                    .andExpect(jsonPath("$.data.priority").value("P2"))
                    .andExpect(jsonPath("$.data.responseDueAt").isNotEmpty())
                    .andExpect(jsonPath("$.data.resolutionDueAt").isNotEmpty());
        }

        @Test
        @DisplayName("Unauthorized user cannot inspect other user's incident SLA record (403 Forbidden)")
        void testUnauthorizedInspectionForbidden() throws Exception {
            var incidentResp = incidentService.createIncident(IncidentCreateRequest.builder()
                    .title("Confidential Incident")
                    .description("Executive issue.")
                    .categoryId(category.getId())
                    .priority(Priority.P1)
                    .severity(Severity.CRITICAL)
                    .build(), employeeUser.getEmail());

            // otherEmployeeUser has no access to employeeUser's incident
            mockMvc.perform(get("/api/sla/records/incident/" + incidentResp.getId())
                            .header("Authorization", "Bearer " + otherEmployeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
    }
}
