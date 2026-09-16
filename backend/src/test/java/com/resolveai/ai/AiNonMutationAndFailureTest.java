package com.resolveai.ai;

import com.resolveai.ai.config.AiConfigProperties;
import com.resolveai.ai.dto.IncidentAnalysisResponse;
import com.resolveai.ai.exception.AiServiceUnavailableException;
import com.resolveai.ai.provider.AiProvider;
import com.resolveai.ai.provider.EmbeddingProvider;
import com.resolveai.ai.rag.RagService;
import com.resolveai.ai.rag.RagTextPreparationService;
import com.resolveai.ai.rag.VectorSearchService;
import com.resolveai.ai.rag.dto.RagAnalysisResponse;
import com.resolveai.ai.service.AiService;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.security.AuthorizationService;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentStatus;
import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.entity.Severity;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.knowledge.repository.KnowledgeArticleRepository;
import com.resolveai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AI & RAG Non-Mutation and Failure Isolation Tests (Phase 13)")
class AiNonMutationAndFailureTest {

    private IncidentRepository incidentRepository;
    private KnowledgeArticleRepository knowledgeArticleRepository;
    private AuthorizationService authorizationService;
    private AiProvider aiProvider;
    private EmbeddingProvider embeddingProvider;
    private VectorSearchService vectorSearchService;
    private RagTextPreparationService ragTextPreparationService;
    private AiConfigProperties aiConfigProperties;

    private AiService aiService;
    private RagService ragService;

    private Incident testIncident;
    private User reporter;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        knowledgeArticleRepository = mock(KnowledgeArticleRepository.class);
        authorizationService = mock(AuthorizationService.class);
        aiProvider = mock(AiProvider.class);
        embeddingProvider = mock(EmbeddingProvider.class);
        vectorSearchService = mock(VectorSearchService.class);
        ragTextPreparationService = mock(RagTextPreparationService.class);

        aiConfigProperties = new AiConfigProperties();
        aiConfigProperties.setEnabled(true);

        aiService = new AiService(incidentRepository, authorizationService, aiProvider, aiConfigProperties);
        ragService = new RagService(
                incidentRepository,
                knowledgeArticleRepository,
                authorizationService,
                embeddingProvider,
                vectorSearchService,
                ragTextPreparationService,
                aiProvider,
                aiConfigProperties
        );

        Role role = Role.builder().id(1L).name("EMPLOYEE").build();
        reporter = User.builder().id(10L).email("emp@resolveai.com").role(role).build();

        testIncident = Incident.builder()
                .id(100L)
                .incidentNumber("INC-2026-000100")
                .title("Intermittent checkout gateway timeouts")
                .description("Users receiving 504 errors at payment checkout step")
                .status(IncidentStatus.NEW)
                .priority(Priority.P1)
                .severity(Severity.CRITICAL)
                .reporter(reporter)
                .assignee(null)
                .team(null)
                .build();

        when(incidentRepository.findById(100L)).thenReturn(Optional.of(testIncident));
        when(authorizationService.canAccessIncident(any(Incident.class))).thenReturn(true);
    }

    @Test
    @DisplayName("AI Triage analysis returns advisory payload and NEVER mutates the incident entity")
    void aiTriageDoesNotMutateIncident() {
        IncidentAnalysisResponse mockAiResponse = IncidentAnalysisResponse.builder()
                .incidentId(100L)
                .suggestedCategory("Payment Gateway")
                .suggestedPriority(Priority.P1)
                .suggestedSeverity(Severity.CRITICAL)
                .summary("Gateway connection timeout")
                .analysis("High error frequency on external provider")
                .build();

        when(aiProvider.analyzeIncident(any())).thenReturn(mockAiResponse);

        IncidentAnalysisResponse response = aiService.analyzeIncident(100L);

        // Verify analysis response
        assertThat(response).isNotNull();
        assertThat(response.getSuggestedPriority()).isEqualTo(Priority.P1);

        // CRITICAL INVARIANT: The incident entity status, priority, severity must remain completely untouched!
        assertThat(testIncident.getStatus()).isEqualTo(IncidentStatus.NEW);
        assertThat(testIncident.getPriority()).isEqualTo(Priority.P1);
        assertThat(testIncident.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(testIncident.getAssignee()).isNull();

        // Repository save should NEVER be called by AI triage
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("RAG analysis returns grounded recommendations and NEVER mutates the incident entity")
    void ragAnalysisDoesNotMutateIncident() {
        when(ragTextPreparationService.prepareIncidentText(any())).thenReturn("Payment gateway 504 timeout");
        when(embeddingProvider.generateEmbedding(anyString())).thenReturn(List.of(0.1, 0.2));
        when(vectorSearchService.searchSimilarArticles(any(), anyInt())).thenReturn(Collections.emptyList());
        when(vectorSearchService.searchSimilarIncidents(any(), anyInt(), anyLong())).thenReturn(Collections.emptyList());

        RagAnalysisResponse mockRagResponse = RagAnalysisResponse.builder()
                .incidentId(100L)
                .diagnosis("Payment provider connection timeout")
                .resolutionSuggestion("Apply aggressive circuit breaker")
                .confidence(0.88)
                .sources(Collections.emptyList())
                .build();

        when(aiProvider.generateRagAnalysis(any())).thenReturn(mockRagResponse);

        RagAnalysisResponse response = ragService.analyzeIncidentWithRag(100L);

        assertThat(response).isNotNull();
        assertThat(response.getConfidence()).isEqualTo(0.88);

        // CRITICAL INVARIANT: The incident entity attributes must remain unmutated
        assertThat(testIncident.getStatus()).isEqualTo(IncidentStatus.NEW);
        assertThat(testIncident.getPriority()).isEqualTo(Priority.P1);
        assertThat(testIncident.getSeverity()).isEqualTo(Severity.CRITICAL);

        // Repository save should NEVER be called by RAG
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("AI failure isolation: when AI provider throws exception, exception is raised cleanly without state corruption")
    void aiProviderFailureThrowsServiceUnavailable() {
        when(aiProvider.analyzeIncident(any())).thenThrow(new RuntimeException("Upstream LLM 503 Overloaded"));

        assertThatThrownBy(() -> aiService.analyzeIncident(100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Upstream LLM 503 Overloaded");

        // Incident remains intact
        assertThat(testIncident.getStatus()).isEqualTo(IncidentStatus.NEW);
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("AI disabled check: when resolveai.ai.enabled=false, throws AiServiceUnavailableException immediately")
    void aiDisabledCheck() {
        aiConfigProperties.setEnabled(false);

        assertThatThrownBy(() -> aiService.analyzeIncident(100L))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("AI service is currently disabled");

        assertThatThrownBy(() -> ragService.analyzeIncidentWithRag(100L))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("AI service is currently disabled");

        verify(aiProvider, never()).analyzeIncident(any());
        verify(aiProvider, never()).generateRagAnalysis(any());
    }

    @Test
    @DisplayName("Resource authorization check: unauthorized user gets AccessDeniedException without calling AI")
    void unauthorizedUserCannotAccessAi() {
        when(authorizationService.canAccessIncident(any(Incident.class))).thenReturn(false);

        assertThatThrownBy(() -> aiService.analyzeIncident(100L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("insufficient permissions");

        assertThatThrownBy(() -> ragService.analyzeIncidentWithRag(100L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("insufficient permissions");

        verify(aiProvider, never()).analyzeIncident(any());
        verify(aiProvider, never()).generateRagAnalysis(any());
    }
}