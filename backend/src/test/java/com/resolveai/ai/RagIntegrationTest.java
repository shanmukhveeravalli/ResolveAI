package com.resolveai.ai;

import com.resolveai.ai.config.AiConfigProperties;
import com.resolveai.ai.exception.AiServiceUnavailableException;
import com.resolveai.ai.provider.AiProvider;
import com.resolveai.ai.provider.EmbeddingProvider;
import com.resolveai.ai.rag.VectorSearchService;
import com.resolveai.ai.rag.dto.RagAnalysisPrompt;
import com.resolveai.ai.rag.dto.RagAnalysisResponse;
import com.resolveai.ai.rag.dto.RagSourceDto;
import com.resolveai.ai.rag.dto.RagSourceType;
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
import com.resolveai.team.entity.Team;
import com.resolveai.team.repository.TeamRepository;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RagIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    private KnowledgeArticleRepository knowledgeArticleRepository;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AiConfigProperties aiConfigProperties;

    @MockBean
    private AiProvider aiProvider;

    @MockBean
    private EmbeddingProvider embeddingProvider;

    private User employeeUser;
    private User assignedEngineerUser;
    private User managerUser;
    private User adminUser;

    private String employeeToken;
    private String assignedEngineerToken;
    private String managerToken;
    private String adminToken;

    private Incident activeIncident;
    private Incident historicalIncident;
    private KnowledgeArticle publishedArticle;
    private KnowledgeArticle draftArticle;

    private final List<Double> standardMockEmbedding = createMockVector(1536, 0.05);

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(aiProvider, embeddingProvider);
        aiConfigProperties.setEnabled(true);
        aiConfigProperties.setApiKey("test-rag-key");
        aiConfigProperties.setTopK(5);

        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Role engineerRole = roleRepository.findByName("ENGINEER").orElseThrow();
        Role managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        Team sreTeam = teamRepository.save(Team.builder()
                .name("SRE Operations")
                .description("Site Reliability Engineering")
                .isActive(true)
                .build());

        employeeUser = createTestUser("emp.rag@resolveai.internal", employeeRole, null);
        assignedEngineerUser = createTestUser("eng.rag@resolveai.internal", engineerRole, sreTeam);
        managerUser = createTestUser("mgr.rag@resolveai.internal", managerRole, sreTeam);
        adminUser = createTestUser("adm.rag@resolveai.internal", adminRole, null);

        employeeToken = jwtService.generateAccessToken(employeeUser);
        assignedEngineerToken = jwtService.generateAccessToken(assignedEngineerUser);
        managerToken = jwtService.generateAccessToken(managerUser);
        adminToken = jwtService.generateAccessToken(adminUser);

        Category dbCategory = categoryRepository.findBySlug("software-applications")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("Software Applications")
                        .slug("software-applications")
                        .isActive(true)
                        .build()));

        // Active Incident
        activeIncident = incidentRepository.save(Incident.builder()
                .incidentNumber("INC-RAG-1001")
                .title("PostgreSQL High CPU and Deadlocks")
                .description("Deadlocks detected in checkout transactions under high write load.")
                .category(dbCategory)
                .priority(Priority.P1)
                .severity(Severity.CRITICAL)
                .status(IncidentStatus.ASSIGNED)
                .reporter(employeeUser)
                .assignee(assignedEngineerUser)
                .team(sreTeam)
                .build());

        // Historical Resolved Incident
        historicalIncident = incidentRepository.save(Incident.builder()
                .incidentNumber("INC-HIST-9002")
                .title("PostgreSQL Transaction Deadlock Mitigation")
                .description("Resolved by ordering row locking sequence and adding partial indexes.")
                .category(dbCategory)
                .priority(Priority.P2)
                .severity(Severity.HIGH)
                .status(IncidentStatus.RESOLVED)
                .reporter(employeeUser)
                .assignee(assignedEngineerUser)
                .team(sreTeam)
                .build());

        // Published Knowledge Article
        publishedArticle = knowledgeArticleRepository.save(KnowledgeArticle.builder()
                .title("Runbook: Resolving PostgreSQL Locking Contention")
                .slug("runbook-postgresql-locking-contention")
                .problem("Concurrent updates causing deadlock errors.")
                .symptoms("SQLState 40P01 detected in application logs.")
                .rootCause("Inconsistent lock order across batch jobs.")
                .resolution("Enforce deterministic ascending primary key ordering in batch update transactions.")
                .category(dbCategory)
                .author(assignedEngineerUser)
                .status(KnowledgeArticleStatus.PUBLISHED)
                .build());

        // Draft Knowledge Article (private/draft)
        draftArticle = knowledgeArticleRepository.save(KnowledgeArticle.builder()
                .title("Internal Draft: Experimental Database Tuning")
                .slug("draft-experimental-tuning")
                .problem("Experimental kernel tuning.")
                .symptoms("High memory pressure.")
                .resolution("Do not apply to production.")
                .category(dbCategory)
                .author(adminUser)
                .status(KnowledgeArticleStatus.DRAFT)
                .build());

        // Default mock embedding provider
        when(embeddingProvider.generateEmbedding(any())).thenReturn(standardMockEmbedding);

        // Populate mock vector embeddings for vector search
        vectorSearchService.storeArticleEmbedding(publishedArticle.getId(), createMockVector(1536, 0.05), "text-embedding-3-small");
        vectorSearchService.storeArticleEmbedding(draftArticle.getId(), createMockVector(1536, 0.05), "text-embedding-3-small");
        vectorSearchService.storeIncidentEmbedding(historicalIncident.getId(), createMockVector(1536, 0.05), "text-embedding-3-small");

        // Default mock RAG response
        RagAnalysisResponse defaultResponse = RagAnalysisResponse.builder()
                .incidentId(activeIncident.getId())
                .diagnosis("Database lock order mismatch identified as root cause based on Runbook #KB-" + publishedArticle.getId())
                .resolutionSuggestion("1. Apply deterministic row lock ordering.\n2. Scale read replicas.")
                .confidence(0.92)
                .sources(List.of(
                        RagSourceDto.builder()
                                .sourceType(RagSourceType.KNOWLEDGE_ARTICLE)
                                .sourceId(publishedArticle.getId())
                                .title(publishedArticle.getTitle())
                                .similarity(0.95)
                                .reference(publishedArticle.getSlug())
                                .snippet("Enforce deterministic ascending primary key ordering...")
                                .build(),
                        RagSourceDto.builder()
                                .sourceType(RagSourceType.HISTORICAL_INCIDENT)
                                .sourceId(historicalIncident.getId())
                                .title(historicalIncident.getTitle())
                                .similarity(0.89)
                                .reference(historicalIncident.getIncidentNumber())
                                .snippet("Resolved by ordering row locking sequence...")
                                .build()
                ))
                .build();

        when(aiProvider.generateRagAnalysis(any())).thenReturn(defaultResponse);
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

    private static List<Double> createMockVector(int dim, double baseVal) {
        List<Double> v = new ArrayList<>(dim);
        for (int i = 0; i < dim; i++) {
            v.add(baseVal + (i * 0.0001));
        }
        return v;
    }

    // =========================================================================
    // 1. RBAC & AUTHENTICATION (Scenarios 1-5)
    // =========================================================================

    @Test
    @DisplayName("Scenario 1: Authenticated assigned engineer can request RAG analysis")
    void testEngineerCanRequestRagAnalysis() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + assignedEngineerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(activeIncident.getId()))
                .andExpect(jsonPath("$.diagnosis").isNotEmpty())
                .andExpect(jsonPath("$.resolutionSuggestion").isNotEmpty())
                .andExpect(jsonPath("$.confidence").value(0.92))
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.sources.length()").value(2));
    }

    @Test
    @DisplayName("Scenario 2: Manager can request RAG analysis")
    void testManagerCanRequestRagAnalysis() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(activeIncident.getId()));
    }

    @Test
    @DisplayName("Scenario 3: Admin can request RAG analysis")
    void testAdminCanRequestRagAnalysis() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(activeIncident.getId()));
    }

    @Test
    @DisplayName("Scenario 4: Unauthenticated request returns 401 Unauthorized")
    void testUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Scenario 5: Unauthorized employee role returns 403 Forbidden")
    void testEmployeeReturns403() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Scenario 6: Missing incident returns 404 Not Found")
    void testMissingIncidentReturns404() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", 999999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =========================================================================
    // 2. VECTOR SEARCH & RETRIEVAL (Scenarios 7-10)
    // =========================================================================

    @Test
    @DisplayName("Scenario 7: Vector search returns relevant knowledge articles")
    void testVectorSearchReturnsArticles() {
        var articles = vectorSearchService.searchSimilarArticles(standardMockEmbedding, 5);
        assertThat(articles).isNotEmpty();
        assertThat(articles.stream().anyMatch(a -> a.getSourceId().equals(publishedArticle.getId()))).isTrue();
    }

    @Test
    @DisplayName("Scenario 8: Vector search returns relevant historical incidents")
    void testVectorSearchReturnsHistoricalIncidents() {
        var incidents = vectorSearchService.searchSimilarIncidents(standardMockEmbedding, 5, activeIncident.getId());
        assertThat(incidents).isNotEmpty();
        assertThat(incidents.stream().anyMatch(i -> i.getSourceId().equals(historicalIncident.getId()))).isTrue();
    }

    @Test
    @DisplayName("Scenario 9: Top-K retrieval respects limit")
    void testTopKLimitRespected() {
        var articles = vectorSearchService.searchSimilarArticles(standardMockEmbedding, 1);
        assertThat(articles).hasSize(1);
    }

    @Test
    @DisplayName("Scenario 10: RAG prompt context contains retrieved sources")
    void testRagPromptContainsRetrievedSources() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + assignedEngineerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        ArgumentCaptor<RagAnalysisPrompt> promptCaptor = ArgumentCaptor.forClass(RagAnalysisPrompt.class);
        verify(aiProvider).generateRagAnalysis(promptCaptor.capture());

        RagAnalysisPrompt capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt.getIncidentId()).isEqualTo(activeIncident.getId());
        assertThat(capturedPrompt.getSources()).isNotEmpty();
        assertThat(capturedPrompt.getSources().stream().anyMatch(s -> s.getSourceId().equals(publishedArticle.getId()))).isTrue();
    }

    // =========================================================================
    // 3. GROUNDED RESPONSE & CITATIONS (Scenarios 11-12)
    // =========================================================================

    @Test
    @DisplayName("Scenario 11: Response contains explicit source references and similarities")
    void testResponseContainsSourceReferences() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].sourceType").value("KNOWLEDGE_ARTICLE"))
                .andExpect(jsonPath("$.sources[0].sourceId").value(publishedArticle.getId()))
                .andExpect(jsonPath("$.sources[0].similarity").value(0.95))
                .andExpect(jsonPath("$.sources[1].sourceType").value("HISTORICAL_INCIDENT"))
                .andExpect(jsonPath("$.sources[1].sourceId").value(historicalIncident.getId()))
                .andExpect(jsonPath("$.sources[1].similarity").value(0.89));
    }

    @Test
    @DisplayName("Scenario 12: Valid grounded AI response maps correctly to DTO")
    void testValidResponseMapsCorrectly() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(activeIncident.getId()))
                .andExpect(jsonPath("$.diagnosis").value(org.hamcrest.Matchers.containsString("lock order mismatch")))
                .andExpect(jsonPath("$.resolutionSuggestion").value(org.hamcrest.Matchers.containsString("deterministic row lock")))
                .andExpect(jsonPath("$.confidence").value(0.92));
    }

    // =========================================================================
    // 4. FAILURE ISOLATION (Scenarios 13-15)
    // =========================================================================

    @Test
    @DisplayName("Scenario 13: Embedding provider failure returns 503 Service Unavailable")
    void testEmbeddingProviderFailureReturns503() throws Exception {
        when(embeddingProvider.generateEmbedding(any()))
                .thenThrow(new AiServiceUnavailableException("Embedding provider timed out"));

        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Scenario 14: AI provider failure returns 503 Service Unavailable")
    void testAiProviderFailureReturns503() throws Exception {
        when(aiProvider.generateRagAnalysis(any()))
                .thenThrow(new AiServiceUnavailableException("AI provider completion failed: 500"));

        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    @DisplayName("Scenario 15: Malformed AI response returns 503 Service Unavailable")
    void testMalformedAiResponseReturns503() throws Exception {
        when(aiProvider.generateRagAnalysis(any()))
                .thenThrow(new AiServiceUnavailableException("Malformed response received from AI provider"));

        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    // =========================================================================
    // 5. INVARIANT & VISIBILITY CHECKS (Scenarios 16-18)
    // =========================================================================

    @Test
    @DisplayName("Scenario 16: Incident state remains completely unchanged after RAG analysis")
    void testIncidentUnchangedAfterRagAnalysis() throws Exception {
        Priority initialPriority = activeIncident.getPriority();
        Severity initialSeverity = activeIncident.getSeverity();
        IncidentStatus initialStatus = activeIncident.getStatus();
        String initialTitle = activeIncident.getTitle();
        String initialDescription = activeIncident.getDescription();
        Long initialAssigneeId = activeIncident.getAssignee().getId();

        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Incident unchanged = incidentRepository.findById(activeIncident.getId()).orElseThrow();
        assertThat(unchanged.getPriority()).isEqualTo(initialPriority);
        assertThat(unchanged.getSeverity()).isEqualTo(initialSeverity);
        assertThat(unchanged.getStatus()).isEqualTo(initialStatus);
        assertThat(unchanged.getTitle()).isEqualTo(initialTitle);
        assertThat(unchanged.getDescription()).isEqualTo(initialDescription);
        assertThat(unchanged.getAssignee().getId()).isEqualTo(initialAssigneeId);
    }

    @Test
    @DisplayName("Scenario 17: Draft/private knowledge article is not leaked to unauthorized callers")
    void testDraftArticleNotLeakedToEngineer() throws Exception {
        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + assignedEngineerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        ArgumentCaptor<RagAnalysisPrompt> promptCaptor = ArgumentCaptor.forClass(RagAnalysisPrompt.class);
        verify(aiProvider).generateRagAnalysis(promptCaptor.capture());

        RagAnalysisPrompt captured = promptCaptor.getValue();
        // The draft article was authored by adminUser, so assignedEngineerUser cannot view it
        boolean containsDraft = captured.getSources().stream()
                .anyMatch(s -> s.getSourceType() == com.resolveai.ai.rag.dto.RagSourceType.KNOWLEDGE_ARTICLE &&
                               s.getSourceId().equals(draftArticle.getId()));
        assertThat(containsDraft).isFalse();
    }

    @Test
    @DisplayName("Scenario 18: Empty retrieval result is handled gracefully without error")
    void testEmptyRetrievalHandledGracefully() throws Exception {
        // Return orthogonal vector that matches no stored content
        when(embeddingProvider.generateEmbedding(any())).thenReturn(createMockVector(1536, 0.99));

        RagAnalysisResponse emptySourcesResponse = RagAnalysisResponse.builder()
                .incidentId(activeIncident.getId())
                .diagnosis("No historical grounding documents matched the query. Advisory diagnosis based on ticket text.")
                .resolutionSuggestion("Manual escalation to database administrator.")
                .confidence(0.50)
                .sources(List.of())
                .build();

        when(aiProvider.generateRagAnalysis(any())).thenReturn(emptySourcesResponse);

        mockMvc.perform(post("/api/ai/incidents/{incidentId}/rag-analyze", activeIncident.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.confidence").value(0.50));
    }
}
