# ResolveAI — Engineering Roadmap & Phase Breakdown

## Roadmap Overview

This roadmap defines the 19 sequential development phases for **ResolveAI**. Each phase builds strictly upon the previous stable milestone. In accordance with enterprise engineering standards:
- **No premature technology introduction** (e.g. Redis is not added before core business logic works; RAG is not introduced before knowledge base and incidents exist).
- **Zero regressions**: The application must compile, pass tests, and remain runnable after every phase.
- **Strict incremental delivery**: Work is scoped to the current phase only.

---

## Phase Breakdown

### Phase 0: Project Planning and Architecture *(Current)*
- **Objective**: Establish architectural blueprints, domain model boundaries, PostgreSQL schema specifications, REST endpoint contracts, and engineering roadmap before writing source code.
- **Deliverables**:
  - `README.md`
  - `docs/architecture/architecture.md`
  - `docs/database/database-design.md`
  - `docs/api/api-plan.md`
  - `docs/development-roadmap.md`
- **Acceptance Criteria**: All system requirements mapped to concrete specifications; no source code or unnecessary dependencies created prematurely.

---

### Phase 1: Backend & Frontend Project Setup
- **Objective**: Scaffold the Java 21 Spring Boot 3 multi-module or clean package project structure and the React 18 / TypeScript / Vite frontend application.
- **Backend Deliverables**:
  - `pom.xml` / `build.gradle` configured with Java 21, Spring Boot 3.3+, Spring Web, Validation, Spring Data JPA, PostgreSQL driver, and Lombok/MapStruct.
  - Basic Spring Boot entry point (`ResolveAiApplication.java`).
  - Base configuration files (`application.yml`, `application-dev.yml`).
  - `.env.example` template for configuration secrets.
  - Health check endpoint `/api/health`.
- **Frontend Deliverables**:
  - Vite + React + TypeScript scaffold (`frontend/`).
  - Routing skeleton (`AppRouter.tsx`), theme/styling foundation (Tailwind CSS).
  - Axios HTTP client instance with base URL configuration.
- **Acceptance Criteria**: Backend compiles and responds with HTTP 200 on `/api/health`. Frontend dev server starts cleanly.

---

### Phase 2: Database Schema & Flyway Migrations
- **Objective**: Set up PostgreSQL connection pooling (HikariCP) and establish versioned Flyway database migrations.
- **Deliverables**:
  - Flyway migration scripts:
    - `V1__init_auth_and_users.sql`
    - `V2__init_categories_and_sla_policies.sql`
    - `V3__init_incidents_and_sla_records.sql`
    - `V4__init_knowledge_base.sql`
    - `V5__init_notifications_and_audit.sql`
    - `V6__seed_initial_data.sql`
  - Base JPA entities (`BaseAuditEntity`, `@MappedSuperclass` with `created_at`, `updated_at`).
  - JPA repository interfaces for initial tables.
- **Acceptance Criteria**: Running `mvn clean test` or starting the application executes Flyway migrations without errors; initial roles and admin user seeded cleanly.

---

### Phase 3: Authentication Module (JWT & BCrypt)
- **Objective**: Implement secure user registration, credential verification with BCrypt, stateless JWT issuance, and token refresh.
- **Deliverables**:
  - `JwtService`: HMAC-SHA256 token generation, signature validation, claims extraction.
  - `JwtAuthenticationFilter`: Request interception, security context population.
  - `AuthService` & `AuthController`:
    - `POST /api/auth/register`
    - `POST /api/auth/login`
    - `POST /api/auth/refresh`
  - Frontend Authentication Pages: Login and Register views with validation, JWT storage in secure memory/storage, and Axios auth interceptor.
- **Acceptance Criteria**: Invalid credentials return 401 Unauthorized; valid credentials return access + refresh tokens; protected endpoints reject requests without valid Bearer tokens.

---

### Phase 4: Role-Based Access Control (RBAC) & User Management
- **Objective**: Enforce role-based (`EMPLOYEE`, `ENGINEER`, `MANAGER`, `ADMIN`) and resource-level authorization across API endpoints and frontend views.
- **Deliverables**:
  - Spring Security `MethodSecurity` enabled (`@PreAuthorize`).
  - User profile endpoint `GET /api/users/me`.
  - Admin user management endpoints (`GET /api/admin/users`, `PATCH /api/admin/users/{id}/role`).
  - Frontend Role Guard component (`ProtectedRoute.tsx`) dynamically rendering navigation based on authenticated role.
- **Acceptance Criteria**: Accessing an admin endpoint as an employee returns HTTP 403 Forbidden; navigation reflects permitted actions per role.

---

### Phase 5: Incident Management Core (Lifecycle & State Machine)
- **Objective**: Implement incident creation, state transition engine with strict validation, incident detail retrieval, comments, and immutable audit history.
- **Deliverables**:
  - `IncidentState` enum and transition validator (`NEW` → `TRIAGED` → `ASSIGNED` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`, plus `ESCALATED`, `REOPENED`).
  - `IncidentService` enforcing transition invariants and appending to `IncidentHistory`.
  - REST endpoints:
    - `GET /api/incidents` (with filtering and pagination)
    - `POST /api/incidents`
    - `GET /api/incidents/{id}`
    - `PATCH /api/incidents/{id}/status`
    - `POST /api/incidents/{id}/comments`
    - `GET /api/incidents/{id}/history`
  - Frontend Incident Views: Incident List, Create Incident form, Incident Details page with comment thread and history timeline.
- **Acceptance Criteria**: Invalid state transitions return HTTP 400 Bad Request with explanation; all status updates create history records; unit tests verify all state machine edges.

---

### Phase 6: Teams and Workload Assignment
- **Objective**: Enable managers and admins to organize engineers into teams and route incidents.
- **Deliverables**:
  - `TeamService` and `TeamController`:
    - `GET /api/teams`, `POST /api/teams`, `GET /api/teams/{id}`
    - `PATCH /api/incidents/{id}/assign` (assign to team and/or engineer)
  - Frontend: Engineer Work Queue view and Team Workload view for Managers.
- **Acceptance Criteria**: Managers can assign tickets to specific teams and engineers; engineers see assigned tickets in their work queue; assignment history is recorded.

---

### Phase 7: SLA Engine & Breach Monitoring
- **Objective**: Implement dynamic SLA calculation based on configurable policies and background breach detection.
- **Deliverables**:
  - SLA Policy configuration entity and endpoints (`GET /api/admin/sla-policies`, `PUT /api/admin/sla-policies/{id}`).
  - Dynamic deadline computation on incident creation (`response_due_at`, `resolution_due_at`).
  - Event listener stamping `responded_at` upon first engineer response and `resolved_at` on resolution.
  - Scheduled background monitor (`@Scheduled(cron = "0 * * * * *")`) detecting impending and breached SLAs.
  - Frontend visual SLA indicators (countdown badges, breach warnings).
- **Acceptance Criteria**: SLA deadlines accurately reflect configured policy minutes; missed deadlines flag `is_response_breached` / `is_resolution_breached`.

---

### Phase 8: In-App Notification System
- **Objective**: Deliver real-time in-app alerts for ticket assignments, status updates, comments, and SLA warnings.
- **Deliverables**:
  - Domain event publication via Spring `ApplicationEventPublisher`.
  - Asynchronous transactional event listener creating rows in `notifications`.
  - REST endpoints:
    - `GET /api/notifications`
    - `PATCH /api/notifications/{id}/read`
  - Frontend notification bell icon with live unread counter and notification popover/list.
- **Acceptance Criteria**: Actions on an incident generate notifications for relevant parties; marking as read updates database state and counter.

---

### Phase 9: Knowledge Base Module
- **Objective**: Create a categorized knowledge repository of technical issues, root causes, and verified solutions.
- **Deliverables**:
  - `KnowledgeService` and `KnowledgeController`:
    - Full CRUD for articles (`GET`, `POST`, `PUT /api/knowledge`)
    - Search endpoint with category and keyword filtering.
    - Incident-to-article linking (`incident_knowledge_links`).
  - Frontend Knowledge Base screens: Article search, Category browsing, Article Detail, Article Editor for engineers/managers.
- **Acceptance Criteria**: Employees can search published articles; engineers can link articles to incidents during investigation.

---

### Phase 10: Analytics & Operational Dashboards
- **Objective**: Calculate and visualize enterprise service desk KPIs derived directly from database queries.
- **Deliverables**:
  - `AnalyticsService` executing optimized aggregation queries:
    - Total, open, critical, and resolved counts.
    - SLA compliance percentage.
    - Mean Time to Resolve (MTTR).
    - Incidents grouped by priority, status, and team.
  - REST endpoint: `GET /api/analytics/overview`.
  - Frontend Dashboard: Visual charts and metric summary cards for managers and admins.
- **Acceptance Criteria**: All displayed metrics correspond to real database records; no mocked or static placeholder data.

---

### Phase 11: AI Foundation (Summarization & Triage Advisory)
- **Objective**: Introduce isolated AI service layer to assist engineers with incident summarization, category suggestion, and severity recommendation.
- **Deliverables**:
  - Abstract `AiAssistantService` interface.
  - Mock and live LLM adapter implementations (`ai.provider=mock` fallback mode).
  - Incident prompt engineering generating structured JSON output.
  - REST endpoint: `POST /api/ai/analyze-incident`.
  - Frontend AI Copilot widget on Incident Details view with "Accept Suggestion" action.
- **Acceptance Criteria**: AI failure or timeout does not break the incident view; recommendations clearly marked as advisory.

---

### Phase 12: RAG & Vector Search (pgvector & Similar Incidents)
- **Objective**: Enhance the AI copilot with Retrieval-Augmented Generation (RAG) using vector similarity search over historical incidents and knowledge articles.
- **Deliverables**:
  - Integration with PostgreSQL `pgvector` or cosine similarity search on text embeddings.
  - Article and incident embedding generation pipeline.
  - Vector similarity query retrieving top K relevant sources.
  - Grounded RAG resolution recommendation with explicit citations of retrieved source IDs.
- **Acceptance Criteria**: AI recommendations cite specific existing articles or past incidents; engineers can accept or reject suggestions.

---

### Phase 13: Redis Caching & Rate Limiting
- **Objective**: Introduce Redis for cache-aside caching of static configuration and API rate limiting on critical endpoints.
- **Deliverables**:
  - Redis configuration & `RedisCacheManager`.
  - `@Cacheable` on category taxonomy and SLA policies with `@CacheEvict` on updates.
  - Rate limiting interceptor on `/api/auth/login` and `/api/ai/*`.
- **Acceptance Criteria**: Category list served from cache on repeat requests; exceeding rate limit returns HTTP 429 Too Many Requests.

---

### Phase 14: Testing & Security Hardening
- **Objective**: Execute comprehensive unit, integration, and security test suites.
- **Deliverables**:
  - Unit tests: Incident state machine transitions, SLA calculation logic, RBAC rules.
  - Integration tests: Repository database constraints, transactional rollbacks.
  - Security tests: Verify HTTP 401 for unauthenticated requests, HTTP 403 for insufficient roles, and resource ownership barriers.
  - AI fault tests: Simulate provider timeout and verify graceful degradation.
- **Acceptance Criteria**: Minimum 80% coverage on core business logic services; zero critical security bypasses.

---

### Phase 15: Docker & Containerization
- **Objective**: Containerize backend, frontend, database, and Redis for single-command deployment.
- **Deliverables**:
  - Multi-stage `Dockerfile` for Spring Boot (using Eclipse Temurin 21 JRE).
  - Multi-stage `Dockerfile` for React frontend (Vite build + Nginx alpine).
  - Production-ready `docker-compose.yml` with health checks, volumes, and network isolation.
- **Acceptance Criteria**: `docker compose up --build` launches PostgreSQL, Redis, backend, and frontend with all health checks passing.

---

### Phase 16: CI/CD Pipeline (GitHub Actions)
- **Objective**: Automate build verification, automated testing, and container packaging on pull requests and commits.
- **Deliverables**:
  - `.github/workflows/ci.yml`: Java 21 setup, Node setup, backend Maven test run, frontend lint & test, Docker build test.
- **Acceptance Criteria**: Pipeline runs cleanly on `main` and `develop` branches.

---

### Phase 17: Cloud Deployment Readiness
- **Objective**: Prepare application configuration for cloud deployment (e.g. AWS ECS / Render / GCP Cloud Run).
- **Deliverables**:
  - Spring Boot Actuator health and metrics configuration (`/actuator/health`).
  - Production profile (`application-prod.yml`) utilizing environment variables.
  - Graceful shutdown configuration.
- **Acceptance Criteria**: Actuator reports healthy status; zero hardcoded secrets in production configuration.

---

### Phase 18: Final Polish, Demo Scenarios & Release
- **Objective**: Complete end-to-end integration walkthrough, demo seed data, screenshots, and operational user guide.
- **Deliverables**:
  - Comprehensive walkthrough documentation.
  - Seed dataset containing realistic corporate enterprise incidents and articles.
  - Verified demo accounts for all 4 roles.
- **Acceptance Criteria**: Complete demo scenarios executable from scratch without manual database adjustments.
