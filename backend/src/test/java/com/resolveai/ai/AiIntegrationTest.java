package com.resolveai.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.ai.config.AiConfigProperties;
import com.resolveai.ai.dto.IncidentAnalysisResponse;
import com.resolveai.ai.exception.AiServiceUnavailableException;
import com.resolveai.ai.provider.AiProvider;
import com.resolveai.ai.provider.OpenAiCompatibleAiProvider;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.auth.security.JwtService;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentStatus;
import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.entity.Severity;
import com.resolveai.incident.repository.CategoryRepository;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AiConfigProperties aiConfigProperties;

    @MockBean
    private AiProvider aiProvider;

    private User employeeUser;
    private User assignedEngineerUser;
    private User unassignedEngineerUser;
    private User managerUser;
    private User adminUser;

    private String employeeToken;
    private String assignedEngineerToken;
    private String unassignedEngineerToken;
    private String managerToken;
    private String adminToken;

    private Incident testIncident;

    @BeforeEach
    void setUp() {
        // Reset properties to enabled for standard test runs
        aiConfigProperties.setEnabled(true);
        aiConfigProperties.setApiKey("test-api-key");
        aiConfigProperties.setModel("gpt-4o-mini");

        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Role engineerRole = roleRepository.findByName("ENGINEER").orElseThrow();
        Role managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        Team supportTeam = teamRepository.save(Team.builder()
                .name("Tier 2 Operations")
                .description("Handles production infrastructure incidents")
                .isActive(true)
                .build());

        Team otherTeam = teamRepository.save(Team.builder()
                .name("Network Operations")
                .description("Handles network issues")
                .isActive(true)
                .build());

        employeeUser = createTestUser("emp.ai@resolveai.internal", employeeRole, null);
        assignedEngineerUser = createTestUser("eng.ai.assigned@resolveai.internal", engineerRole, supportTeam);
        unassignedEngineerUser = createTestUser("eng.ai.unassigned@resolveai.internal", engineerRole, otherTeam);
        managerUser = createTestUser("mgr.ai@resolveai.internal", managerRole, supportTeam);
        adminUser = createTestUser("adm.ai@resolveai.internal", adminRole, null);

        employeeToken = jwtService.generateAccessToken(employeeUser);
        assignedEngineerToken = jwtService.generateAccessToken(assignedEngineerUser);
        unassignedEngineerToken = jwtService.generateAccessToken(unassignedEngineerUser);
        managerToken = jwtService.generateAccessToken(managerUser);
        adminToken = jwtService.generateAccessToken(adminUser);

        var category = categoryRepository.findBySlug("software-applications")
                .orElseGet(() -> categoryRepository.save(com.resolveai.incident.entity.Category.builder()
                        .name("Software Applications")
                        .slug("software-applications")
                        .isActive(true)
                        .build()));

        testIncident = incidentRepository.save(Incident.builder()
                .incidentNumber("INC-20260907-9001")
                .title("Postgres HikariCP Connection Pool Exhaustion")
                .description("Production checkout service failing with ConnectionTimeoutException under load.")
                .category(category)
                .priority(Priority.P2)
                .severity(Severity.HIGH)
                .status(IncidentStatus.ASSIGNED)
                .reporter(employeeUser)
                .assignee(assignedEngineerUser)
                .team(supportTeam)
                .build());

        // Default mocked AI provider response
        IncidentAnalysisResponse defaultMockResponse = IncidentAnalysisResponse.builder()
                .incidentId(testIncident.getId())
                .suggestedCategory("Database & Storage")
                .suggestedPriority(Priority.P1)
                .suggestedSeverity(Severity.CRITICAL)
                .summary("Connection pool saturation on primary relational database.")
                .analysis("Increase HikariCP maximumPoolSize or optimize long-running transactions holding connections.")
                .build();

        when(aiProvider.analyzeIncident(any())).thenReturn(defaultMockResponse);
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
    // 1. UNAUTHENTICATED REQUESTS
    // =========================================================================
    @Test
    @DisplayName("Scenario 1: Unauthenticated request returns 401 Unauthorized")
    void testUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. ROLE AUTHORIZATION (RBAC)
    // =========================================================================
    @Test
    @DisplayName("Scenario 2: Employee role returns 403 Forbidden")
    void testEmployeeForbiddenReturns403() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Scenario 3: Authorized assigned engineer returns 200 OK")
    void testAuthorizedEngineerReturns200() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .header("Authorization", "Bearer " + assignedEngineerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(testIncident.getId()))
                .andExpect(jsonPath("$.suggestedCategory").value("Database & Storage"))
                .andExpect(jsonPath("$.suggestedPriority").value("P1"))
                .andExpect(jsonPath("$.suggestedSeverity").value("CRITICAL"))
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.analysis").isNotEmpty());
    }

    @Test
    @DisplayName("Scenario 4: Manager returns 200 OK")
    void testManagerReturns200() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(testIncident.getId()))
                .andExpect(jsonPath("$.suggestedPriority").value("P1"));
    }

    @Test
    @DisplayName("Scenario 5: Admin returns 200 OK")
    void testAdminReturns200() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(testIncident.getId()));
    }

    @Test
    @DisplayName("Scenario 6: Engineer without incident access returns 403 Forbidden")
    void testEngineerWithoutIncidentAccessReturns403() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .header("Authorization", "Bearer " + unassignedEngineerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // =========================================================================
    // 3. RESOURCE NOT FOUND
    // =========================================================================
    @Test
    @DisplayName("Scenario 7: Nonexistent incident returns 404 Not Found")
    void testNonexistentIncidentReturns404() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", 999999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // =========================================================================
    // 4. FAILURE ISOLATION & UNAVAILABILITY
    // =========================================================================
    @Test
    @DisplayName("Scenario 8: AI subsystem disabled returns 503 Service Unavailable")
    void testAiDisabledReturns503() throws Exception {
        aiConfigProperties.setEnabled(false);

        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("disabled")));
    }

    @Test
    @DisplayName("Scenario 9: Provider timeout returns 503 Service Unavailable")
    void testProviderTimeoutReturns503() throws Exception {
        when(aiProvider.analyzeIncident(any())).thenThrow(
                new AiServiceUnavailableException("AI provider timed out or connection failed",
                        new ResourceAccessException("Read timed out"))
        );

        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Scenario 10: Provider failure / HTTP error returns 503 Service Unavailable")
    void testProviderFailureReturns503() throws Exception {
        when(aiProvider.analyzeIncident(any())).thenThrow(
                new AiServiceUnavailableException("AI provider returned an error: 500 INTERNAL_SERVER_ERROR")
        );

        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Scenario 11: Malformed provider response returns 503 Service Unavailable")
    void testMalformedProviderResponseReturns503() throws Exception {
        when(aiProvider.analyzeIncident(any())).thenThrow(
                new AiServiceUnavailableException("Malformed response received from AI provider")
        );

        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    // =========================================================================
    // 5. STRUCTURED MAPPING & NON-MUTATION
    // =========================================================================
    @Test
    @DisplayName("Scenario 12: Valid provider response maps correctly to DTO")
    void testValidProviderResponseMapsCorrectly() throws Exception {
        IncidentAnalysisResponse customResponse = IncidentAnalysisResponse.builder()
                .incidentId(testIncident.getId())
                .suggestedCategory("Infrastructure & Cloud")
                .suggestedPriority(Priority.P4)
                .suggestedSeverity(Severity.LOW)
                .summary("Non-urgent configuration adjustment needed.")
                .analysis("Adjust health check interval to reduce false positive alerts.")
                .build();

        when(aiProvider.analyzeIncident(any())).thenReturn(customResponse);

        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(testIncident.getId()))
                .andExpect(jsonPath("$.suggestedCategory").value("Infrastructure & Cloud"))
                .andExpect(jsonPath("$.suggestedPriority").value("P4"))
                .andExpect(jsonPath("$.suggestedSeverity").value("LOW"))
                .andExpect(jsonPath("$.summary").value("Non-urgent configuration adjustment needed."))
                .andExpect(jsonPath("$.analysis").value("Adjust health check interval to reduce false positive alerts."));
    }

    @Test
    @DisplayName("Scenario 13: Incident state remains completely unchanged after AI analysis")
    void testIncidentRemainsUnchangedAfterAiAnalysis() throws Exception {
        Priority initialPriority = testIncident.getPriority();
        Severity initialSeverity = testIncident.getSeverity();
        IncidentStatus initialStatus = testIncident.getStatus();
        String initialTitle = testIncident.getTitle();
        String initialDescription = testIncident.getDescription();
        String initialCategoryName = testIncident.getCategory().getName();
        Long initialAssigneeId = testIncident.getAssignee().getId();
        Long initialReporterId = testIncident.getReporter().getId();

        mockMvc.perform(post("/api/ai/incidents/{incidentId}/analyze", testIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Reload incident from persistence
        Incident unchanged = incidentRepository.findById(testIncident.getId()).orElseThrow();
        assertThat(unchanged.getPriority()).isEqualTo(initialPriority);
        assertThat(unchanged.getSeverity()).isEqualTo(initialSeverity);
        assertThat(unchanged.getStatus()).isEqualTo(initialStatus);
        assertThat(unchanged.getTitle()).isEqualTo(initialTitle);
        assertThat(unchanged.getDescription()).isEqualTo(initialDescription);
        assertThat(unchanged.getCategory().getName()).isEqualTo(initialCategoryName);
        assertThat(unchanged.getAssignee().getId()).isEqualTo(initialAssigneeId);
        assertThat(unchanged.getReporter().getId()).isEqualTo(initialReporterId);
    }

    // =========================================================================
    // 6. UNIT VALIDATION FOR CONCRETE OpenAiCompatibleAiProvider
    // =========================================================================
    @Nested
    @DisplayName("OpenAiCompatibleAiProvider Isolated Unit Checks")
    class OpenAiCompatibleAiProviderUnitTests {

        @Test
        @DisplayName("Throws 503 when disabled")
        void testProviderThrowsWhenDisabled() {
            AiConfigProperties props = new AiConfigProperties();
            props.setEnabled(false);

            OpenAiCompatibleAiProvider provider = new OpenAiCompatibleAiProvider(RestClient.builder().build(), props, objectMapper);

            assertThatThrownBy(() -> provider.analyzeIncident(com.resolveai.ai.dto.IncidentAnalysisPrompt.builder().incidentId(1L).build()))
                    .isInstanceOf(AiServiceUnavailableException.class)
                    .hasMessageContaining("disabled");
        }

        @Test
        @DisplayName("Throws 503 when API key is missing")
        void testProviderThrowsWhenApiKeyMissing() {
            AiConfigProperties props = new AiConfigProperties();
            props.setEnabled(true);
            props.setApiKey("");

            OpenAiCompatibleAiProvider provider = new OpenAiCompatibleAiProvider(RestClient.builder().build(), props, objectMapper);

            assertThatThrownBy(() -> provider.analyzeIncident(com.resolveai.ai.dto.IncidentAnalysisPrompt.builder().incidentId(1L).build()))
                    .isInstanceOf(AiServiceUnavailableException.class)
                    .hasMessageContaining("API key is not configured");
        }
    }
}
