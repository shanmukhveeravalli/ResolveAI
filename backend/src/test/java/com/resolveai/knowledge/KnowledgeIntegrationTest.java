package com.resolveai.knowledge;

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
import com.resolveai.knowledge.dto.KnowledgeArticleCreateRequest;
import com.resolveai.knowledge.dto.KnowledgeArticleUpdateRequest;
import com.resolveai.knowledge.entity.KnowledgeArticle;
import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import com.resolveai.knowledge.repository.IncidentKnowledgeLinkRepository;
import com.resolveai.knowledge.repository.KnowledgeArticleRepository;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KnowledgeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private KnowledgeArticleRepository knowledgeArticleRepository;

    @Autowired
    private IncidentKnowledgeLinkRepository incidentKnowledgeLinkRepository;

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
    private User engineerAuthor;
    private User otherEngineer;
    private User managerUser;
    private User adminUser;

    private String employeeToken;
    private String otherEmployeeToken;
    private String engineerAuthorToken;
    private String otherEngineerToken;
    private String managerToken;
    private String adminToken;

    private Category networkCategory;
    private Category softwareCategory;
    private Incident testIncident;

    @BeforeEach
    void setUp() {
        employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        engineerRole = roleRepository.findByName("ENGINEER").orElseThrow();
        managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        employeeUser = createTestUser("kb.emp@resolveai.internal", employeeRole);
        otherEmployeeUser = createTestUser("kb.emp2@resolveai.internal", employeeRole);
        engineerAuthor = createTestUser("kb.eng.author@resolveai.internal", engineerRole);
        otherEngineer = createTestUser("kb.eng.other@resolveai.internal", engineerRole);
        managerUser = createTestUser("kb.mgr@resolveai.internal", managerRole);
        adminUser = createTestUser("kb.adm@resolveai.internal", adminRole);

        employeeToken = jwtService.generateAccessToken(employeeUser);
        otherEmployeeToken = jwtService.generateAccessToken(otherEmployeeUser);
        engineerAuthorToken = jwtService.generateAccessToken(engineerAuthor);
        otherEngineerToken = jwtService.generateAccessToken(otherEngineer);
        managerToken = jwtService.generateAccessToken(managerUser);
        adminToken = jwtService.generateAccessToken(adminUser);

        networkCategory = categoryRepository.findBySlug("network-connectivity")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("Network Connectivity")
                        .slug("network-connectivity")
                        .isActive(true)
                        .build()));

        softwareCategory = categoryRepository.findBySlug("software-applications")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("Software Applications")
                        .slug("software-applications")
                        .isActive(true)
                        .build()));

        testIncident = incidentRepository.save(Incident.builder()
                .incidentNumber("INC-KB-2026-001")
                .title("VPN Connectivity Timeout")
                .description("Users cannot connect to VPN")
                .status(IncidentStatus.NEW)
                .priority(Priority.P2)
                .severity(Severity.HIGH)
                .category(networkCategory)
                .reporter(employeeUser)
                .build());
    }

    private User createTestUser(String email, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName(role.getName())
                .role(role)
                .isActive(true)
                .build()));
    }

    // =========================================================================
    // 1. ARTICLE CREATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Article Creation Tests")
    class CreationTests {

        @Test
        @DisplayName("Engineer creates article successfully with default DRAFT status")
        void createArticle_byEngineer_success() throws Exception {
            KnowledgeArticleCreateRequest request = KnowledgeArticleCreateRequest.builder()
                    .title("VPN Gateway Connection Timeout Guide")
                    .problem("Clients cannot connect to VPN")
                    .symptoms("Error 800 received in client logs")
                    .rootCause("Certificate expiration on gateway")
                    .resolution("Renew SSL cert and restart gateway service")
                    .categoryId(networkCategory.getId())
                    .tags("vpn,network,ssl")
                    .build();

            mockMvc.perform(post("/api/knowledge/articles")
                            .header("Authorization", "Bearer " + engineerAuthorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.title").value("VPN Gateway Connection Timeout Guide"))
                    .andExpect(jsonPath("$.data.slug").value("vpn-gateway-connection-timeout-guide"))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.authorId").value(engineerAuthor.getId()))
                    .andExpect(jsonPath("$.data.categoryName").value(networkCategory.getName()))
                    .andExpect(jsonPath("$.data.publishedAt").doesNotExist());
        }

        @Test
        @DisplayName("Creating article with content only populates problem and resolution")
        void createArticle_withContentOnly_success() throws Exception {
            KnowledgeArticleCreateRequest request = KnowledgeArticleCreateRequest.builder()
                    .title("Kubernetes Pod Restart Instructions")
                    .content("Execute kubectl rollout restart deployment/app in production cluster.")
                    .categoryId(softwareCategory.getId())
                    .build();

            mockMvc.perform(post("/api/knowledge/articles")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.title").value("Kubernetes Pod Restart Instructions"))
                    .andExpect(jsonPath("$.data.content").value(containsString("kubectl rollout restart")))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
        }

        @Test
        @DisplayName("Employee cannot create articles (403 Forbidden)")
        void createArticle_byEmployee_forbidden() throws Exception {
            KnowledgeArticleCreateRequest request = KnowledgeArticleCreateRequest.builder()
                    .title("Unauthorized Employee Article")
                    .content("Some content")
                    .build();

            mockMvc.perform(post("/api/knowledge/articles")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Unauthenticated request to create article returns 401")
        void createArticle_unauthenticated_401() throws Exception {
            KnowledgeArticleCreateRequest request = KnowledgeArticleCreateRequest.builder()
                    .title("Unauthenticated Article")
                    .content("Some content")
                    .build();

            mockMvc.perform(post("/api/knowledge/articles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Create article validation fails with blank title (400 Bad Request)")
        void createArticle_validationFailure_blankTitle() throws Exception {
            KnowledgeArticleCreateRequest request = KnowledgeArticleCreateRequest.builder()
                    .title("")
                    .content("Valid content")
                    .build();

            mockMvc.perform(post("/api/knowledge/articles")
                            .header("Authorization", "Bearer " + engineerAuthorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }
    }

    // =========================================================================
    // 2. ARTICLE UPDATE TESTS
    // =========================================================================
    @Nested
    @DisplayName("Article Update Tests")
    class UpdateTests {

        private KnowledgeArticle article;

        @BeforeEach
        void createInitialArticle() {
            article = knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("Initial Article Title")
                    .slug("initial-article-title")
                    .problem("Initial Problem")
                    .symptoms("Initial Symptoms")
                    .resolution("Initial Resolution")
                    .category(networkCategory)
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.DRAFT)
                    .build());
        }

        @Test
        @DisplayName("Author engineer updates article successfully")
        void updateArticle_byAuthor_success() throws Exception {
            KnowledgeArticleUpdateRequest request = KnowledgeArticleUpdateRequest.builder()
                    .title("Updated Article Title")
                    .resolution("Updated Resolution with clear steps")
                    .tags("updated,tested")
                    .build();

            mockMvc.perform(put("/api/knowledge/articles/" + article.getId())
                            .header("Authorization", "Bearer " + engineerAuthorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("Updated Article Title"))
                    .andExpect(jsonPath("$.data.slug").value("updated-article-title"))
                    .andExpect(jsonPath("$.data.resolution").value("Updated Resolution with clear steps"))
                    .andExpect(jsonPath("$.data.tags").value("updated,tested"));
        }

        @Test
        @DisplayName("Manager can update any engineer's article")
        void updateArticle_byManager_success() throws Exception {
            KnowledgeArticleUpdateRequest request = KnowledgeArticleUpdateRequest.builder()
                    .title("Manager Revised Title")
                    .build();

            mockMvc.perform(put("/api/knowledge/articles/" + article.getId())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("Manager Revised Title"));
        }

        @Test
        @DisplayName("Other engineer cannot update article (403 Forbidden)")
        void updateArticle_byOtherEngineer_forbidden() throws Exception {
            KnowledgeArticleUpdateRequest request = KnowledgeArticleUpdateRequest.builder()
                    .title("Malicious Overwrite Title")
                    .build();

            mockMvc.perform(put("/api/knowledge/articles/" + article.getId())
                            .header("Authorization", "Bearer " + otherEngineerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Employee cannot update article (403 Forbidden)")
        void updateArticle_byEmployee_forbidden() throws Exception {
            KnowledgeArticleUpdateRequest request = KnowledgeArticleUpdateRequest.builder()
                    .title("Employee Overwrite Title")
                    .build();

            mockMvc.perform(put("/api/knowledge/articles/" + article.getId())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Update non-existent article returns 404 Not Found")
        void updateArticle_notFound_404() throws Exception {
            KnowledgeArticleUpdateRequest request = KnowledgeArticleUpdateRequest.builder()
                    .title("New Title")
                    .build();

            mockMvc.perform(put("/api/knowledge/articles/999999")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }
    }

    // =========================================================================
    // 3. LIFECYCLE STATE MACHINE TESTS (PUBLISH, ARCHIVE, INVALID TRANSITIONS)
    // =========================================================================
    @Nested
    @DisplayName("Lifecycle State Machine Tests")
    class LifecycleTests {

        private KnowledgeArticle draftArticle;
        private KnowledgeArticle publishedArticle;
        private KnowledgeArticle archivedArticle;

        @BeforeEach
        void setupArticles() {
            draftArticle = knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("Draft Knowledge Article")
                    .slug("draft-knowledge-article")
                    .problem("Draft Problem")
                    .symptoms("Draft Symptoms")
                    .resolution("Draft Resolution")
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.DRAFT)
                    .build());

            publishedArticle = knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("Published Knowledge Article")
                    .slug("published-knowledge-article")
                    .problem("Published Problem")
                    .symptoms("Published Symptoms")
                    .resolution("Published Resolution")
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.PUBLISHED)
                    .publishedAt(Instant.now())
                    .build());

            archivedArticle = knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("Archived Knowledge Article")
                    .slug("archived-knowledge-article")
                    .problem("Archived Problem")
                    .symptoms("Archived Symptoms")
                    .resolution("Archived Resolution")
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.ARCHIVED)
                    .build());
        }

        @Test
        @DisplayName("Publish article transitions DRAFT to PUBLISHED and sets publishedAt")
        void publishArticle_fromDraft_success() throws Exception {
            mockMvc.perform(patch("/api/knowledge/articles/" + draftArticle.getId() + "/publish")
                            .header("Authorization", "Bearer " + engineerAuthorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());

            KnowledgeArticle updated = knowledgeArticleRepository.findById(draftArticle.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(KnowledgeArticleStatus.PUBLISHED);
            assertThat(updated.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("Archive article transitions PUBLISHED to ARCHIVED")
        void archiveArticle_fromPublished_success() throws Exception {
            mockMvc.perform(patch("/api/knowledge/articles/" + publishedArticle.getId() + "/archive")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

            KnowledgeArticle updated = knowledgeArticleRepository.findById(publishedArticle.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(KnowledgeArticleStatus.ARCHIVED);
        }

        @Test
        @DisplayName("Archive article transitions DRAFT to ARCHIVED")
        void archiveArticle_fromDraft_success() throws Exception {
            mockMvc.perform(patch("/api/knowledge/articles/" + draftArticle.getId() + "/archive")
                            .header("Authorization", "Bearer " + engineerAuthorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

            KnowledgeArticle updated = knowledgeArticleRepository.findById(draftArticle.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(KnowledgeArticleStatus.ARCHIVED);
        }

        @Test
        @DisplayName("Reject invalid lifecycle transition: ARCHIVED -> PUBLISHED (400 Bad Request)")
        void invalidLifecycleTransition_archivedToPublished_400() throws Exception {
            mockMvc.perform(patch("/api/knowledge/articles/" + archivedArticle.getId() + "/publish")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"))
                    .andExpect(jsonPath("$.message").value(containsString("ARCHIVED")));
        }

        @Test
        @DisplayName("Reject invalid lifecycle transition: ARCHIVED -> ARCHIVED (400 Bad Request)")
        void invalidLifecycleTransition_archivedToArchived_400() throws Exception {
            mockMvc.perform(patch("/api/knowledge/articles/" + archivedArticle.getId() + "/archive")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
        }

        @Test
        @DisplayName("Reject invalid transition via PUT update: PUBLISHED -> DRAFT (400 Bad Request)")
        void invalidLifecycleTransition_viaPut_400() throws Exception {
            KnowledgeArticleUpdateRequest request = KnowledgeArticleUpdateRequest.builder()
                    .status(KnowledgeArticleStatus.DRAFT)
                    .build();

            mockMvc.perform(put("/api/knowledge/articles/" + publishedArticle.getId())
                            .header("Authorization", "Bearer " + engineerAuthorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
        }

        @Test
        @DisplayName("Employee cannot publish articles (403 Forbidden)")
        void publishArticle_byEmployee_forbidden() throws Exception {
            mockMvc.perform(patch("/api/knowledge/articles/" + draftArticle.getId() + "/publish")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Non-author engineer cannot publish another engineer's draft (403 Forbidden)")
        void publishArticle_byOtherEngineer_forbidden() throws Exception {
            mockMvc.perform(patch("/api/knowledge/articles/" + draftArticle.getId() + "/publish")
                            .header("Authorization", "Bearer " + otherEngineerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Publish non-existent article returns 404 Not Found")
        void publishArticle_notFound_404() throws Exception {
            mockMvc.perform(patch("/api/knowledge/articles/999999/publish")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }
    }

    // =========================================================================
    // 4. RETRIEVAL & VISIBILITY RULES TESTS
    // =========================================================================
    @Nested
    @DisplayName("Retrieval and Visibility Rules Tests")
    class RetrievalTests {

        private KnowledgeArticle publishedArticle;
        private KnowledgeArticle draftArticle;
        private KnowledgeArticle archivedArticle;

        @BeforeEach
        void createArticles() {
            publishedArticle = knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("Published Guide")
                    .slug("published-guide")
                    .problem("Published Problem")
                    .symptoms("Published Symptoms")
                    .resolution("Published Resolution")
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.PUBLISHED)
                    .publishedAt(Instant.now())
                    .build());

            draftArticle = knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("Draft Guide")
                    .slug("draft-guide")
                    .problem("Draft Problem")
                    .symptoms("Draft Symptoms")
                    .resolution("Draft Resolution")
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.DRAFT)
                    .build());

            archivedArticle = knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("Archived Guide")
                    .slug("archived-guide")
                    .problem("Archived Problem")
                    .symptoms("Archived Symptoms")
                    .resolution("Archived Resolution")
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.ARCHIVED)
                    .build());
        }

        @Test
        @DisplayName("Employee can retrieve published article")
        void retrievePublishedArticle_byEmployee_success() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles/" + publishedArticle.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(publishedArticle.getId()))
                    .andExpect(jsonPath("$.data.title").value("Published Guide"));
        }

        @Test
        @DisplayName("Employee cannot retrieve draft article (403 Forbidden)")
        void retrieveDraftArticle_byEmployee_forbidden() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles/" + draftArticle.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Other engineer cannot retrieve draft article (403 Forbidden)")
        void retrieveDraftArticle_byOtherEngineer_forbidden() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles/" + draftArticle.getId())
                            .header("Authorization", "Bearer " + otherEngineerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Author engineer can retrieve their own draft article")
        void retrieveDraftArticle_byAuthor_success() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles/" + draftArticle.getId())
                            .header("Authorization", "Bearer " + engineerAuthorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(draftArticle.getId()))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
        }

        @Test
        @DisplayName("Manager can retrieve any draft article")
        void retrieveDraftArticle_byManager_success() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles/" + draftArticle.getId())
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(draftArticle.getId()));
        }

        @Test
        @DisplayName("Employee cannot retrieve archived article (403 Forbidden)")
        void retrieveArchivedArticle_byEmployee_forbidden() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles/" + archivedArticle.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Get non-existent article returns 404 Not Found")
        void retrieveArticle_notFound_404() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles/999999")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Unauthenticated retrieval returns 401 Unauthorized")
        void retrieveArticle_unauthenticated_401() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles/" + publishedArticle.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 5. SEARCH & CATEGORY FILTERING TESTS
    // =========================================================================
    @Nested
    @DisplayName("Search & Filtering Tests")
    class SearchAndFilterTests {

        @BeforeEach
        void createDiverseArticles() {
            knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("Reset Active Directory Password")
                    .slug("reset-ad-password")
                    .problem("User forgot domain password")
                    .symptoms("Account locked out")
                    .resolution("Reset in ADUC and check unlock checkbox")
                    .category(softwareCategory)
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.PUBLISHED)
                    .publishedAt(Instant.now())
                    .tags("identity,active-directory,password")
                    .build());

            knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("Cisco AnyConnect VPN Gateway Configuration")
                    .slug("cisco-anyconnect-vpn")
                    .problem("Split tunneling routing error")
                    .symptoms("Internal subnets unreachable")
                    .resolution("Update route list in ASA profile")
                    .category(networkCategory)
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.PUBLISHED)
                    .publishedAt(Instant.now())
                    .tags("vpn,cisco,network")
                    .build());

            knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("Secret Network Draft Architecture")
                    .slug("secret-network-draft")
                    .problem("Draft problem")
                    .symptoms("Draft symptoms")
                    .resolution("Draft resolution")
                    .category(networkCategory)
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.DRAFT)
                    .build());
        }

        @Test
        @DisplayName("Database-backed search finds matching articles case-insensitively")
        void search_caseInsensitive_success() throws Exception {
            // Search "password"
            mockMvc.perform(get("/api/knowledge/articles")
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("search", "password"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].title").value("Reset Active Directory Password"));

            // Search uppercase "PASSWORD"
            mockMvc.perform(get("/api/knowledge/articles")
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("search", "PASSWORD"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].title").value("Reset Active Directory Password"));
        }

        @Test
        @DisplayName("Search across content/resolution field")
        void search_acrossResolutionField_success() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles")
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("search", "tunneling"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].title").value("Cisco AnyConnect VPN Gateway Configuration"));
        }

        @Test
        @DisplayName("Filter by category ID")
        void filterByCategoryId_success() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles")
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("categoryId", networkCategory.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].categoryName").value(networkCategory.getName()));
        }

        @Test
        @DisplayName("Employee listing automatically omits drafts")
        void employeeListing_omitsDrafts() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2))) // Only the 2 published articles
                    .andExpect(jsonPath("$.data.content[*].status", everyItem(is("PUBLISHED"))));
        }

        @Test
        @DisplayName("Author engineer listing includes own drafts")
        void authorEngineerListing_includesOwnDrafts() throws Exception {
            mockMvc.perform(get("/api/knowledge/articles")
                            .header("Authorization", "Bearer " + engineerAuthorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(3))); // 2 published + 1 own draft
        }

        @Test
        @DisplayName("Aliased /api/knowledge endpoint works identically")
        void aliasedEndpoint_success() throws Exception {
            mockMvc.perform(get("/api/knowledge")
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("search", "password"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)));
        }
    }

    // =========================================================================
    // 6. INCIDENT ↔ KNOWLEDGE LINKING TESTS
    // =========================================================================
    @Nested
    @DisplayName("Incident Knowledge Linking Tests")
    class LinkingTests {

        private KnowledgeArticle article;

        @BeforeEach
        void createPublishedArticle() {
            article = knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("VPN Troubleshooting SOP")
                    .slug("vpn-troubleshooting-sop")
                    .problem("VPN Connection failures")
                    .symptoms("Timeout")
                    .resolution("Restart service")
                    .category(networkCategory)
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.PUBLISHED)
                    .publishedAt(Instant.now())
                    .build());
        }

        @Test
        @DisplayName("Link knowledge article to incident successfully (201 Created)")
        void linkArticle_success() throws Exception {
            mockMvc.perform(post("/api/incidents/" + testIncident.getId() + "/knowledge/" + article.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.incidentId").value(testIncident.getId()))
                    .andExpect(jsonPath("$.data.articleId").value(article.getId()))
                    .andExpect(jsonPath("$.data.relevanceScore").value(1.0000));

            assertThat(incidentKnowledgeLinkRepository.existsByIncidentIdAndArticleId(
                    testIncident.getId(), article.getId())).isTrue();
        }

        @Test
        @DisplayName("Duplicate linking of same article to incident returns 409 Conflict")
        void linkArticle_duplicatePrevention_409() throws Exception {
            // First link
            mockMvc.perform(post("/api/incidents/" + testIncident.getId() + "/knowledge/" + article.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isCreated());

            // Duplicate link attempt
            mockMvc.perform(post("/api/incidents/" + testIncident.getId() + "/knowledge/" + article.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CONFLICT"));
        }

        @Test
        @DisplayName("Unlink knowledge article removes the relation successfully (200 OK)")
        void unlinkArticle_success() throws Exception {
            // First link
            mockMvc.perform(post("/api/incidents/" + testIncident.getId() + "/knowledge/" + article.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isCreated());

            // Unlink
            mockMvc.perform(delete("/api/incidents/" + testIncident.getId() + "/knowledge/" + article.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Knowledge article unlinked successfully"));

            assertThat(incidentKnowledgeLinkRepository.existsByIncidentIdAndArticleId(
                    testIncident.getId(), article.getId())).isFalse();
        }

        @Test
        @DisplayName("Unlinking a link that does not exist returns 404 Not Found")
        void unlinkArticle_notFound_404() throws Exception {
            mockMvc.perform(delete("/api/incidents/" + testIncident.getId() + "/knowledge/" + article.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Get linked knowledge articles returns list")
        void getLinkedArticles_success() throws Exception {
            mockMvc.perform(post("/api/incidents/" + testIncident.getId() + "/knowledge/" + article.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/incidents/" + testIncident.getId() + "/knowledge")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].articleId").value(article.getId()))
                    .andExpect(jsonPath("$.data[0].articleTitle").value("VPN Troubleshooting SOP"));
        }

        @Test
        @DisplayName("Linking non-existent incident returns 404 Not Found")
        void linkArticle_missingIncident_404() throws Exception {
            mockMvc.perform(post("/api/incidents/999999/knowledge/" + article.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Linking non-existent article returns 404 Not Found")
        void linkArticle_missingArticle_404() throws Exception {
            mockMvc.perform(post("/api/incidents/" + testIncident.getId() + "/knowledge/999999")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("User without access to incident cannot link articles (403 Forbidden)")
        void linkArticle_unauthorizedIncident_403() throws Exception {
            // otherEmployee did not report testIncident and has no team access
            mockMvc.perform(post("/api/incidents/" + testIncident.getId() + "/knowledge/" + article.getId())
                            .header("Authorization", "Bearer " + otherEmployeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Employee cannot link a draft article (403 Forbidden)")
        void linkArticle_draftArticleByEmployee_403() throws Exception {
            KnowledgeArticle draft = knowledgeArticleRepository.save(KnowledgeArticle.builder()
                    .title("Secret Draft")
                    .slug("secret-draft")
                    .problem("Problem")
                    .symptoms("Symptoms")
                    .resolution("Resolution")
                    .author(engineerAuthor)
                    .status(KnowledgeArticleStatus.DRAFT)
                    .build());

            mockMvc.perform(post("/api/incidents/" + testIncident.getId() + "/knowledge/" + draft.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
    }
}
