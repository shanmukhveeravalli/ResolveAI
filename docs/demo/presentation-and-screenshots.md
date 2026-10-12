# ResolveAI — Project Showcase, Screenshot Checklist & Presentation Guide

This document provides a comprehensive presentation guide and manual screenshot checklist for technical evaluations, portfolio demonstrations, and academic or hiring interviews.

---

## 1. Manual Screenshot Checklist

Capture these screenshots from a running instance to demonstrate full-stack completion:

| # | Scene / Component | Target URL or Action | Key Visual Elements to Highlight |
|---|---|---|---|
| **SS-01** | **Platform Overview (Home)** | `http://localhost/` (Tab: "Platform Overview") | Backend health badge (`UP`), System Architecture cards, Core Domain Bounded Contexts, and quick navigation. |
| **SS-02** | **Operations Analytics Dashboard** | `http://localhost/` (Tab: "Operations Analytics") | Incident KPI summary cards (Total, Open, Resolved, Critical P1), MTTR calculation, SLA compliance rate badge, and Status/Priority distribution charts. |
| **SS-03** | **Team Workload Breakdown** | `http://localhost/` (Tab: "Operations Analytics") | Workload distribution chart showing active tickets assigned per engineering squad. |
| **SS-04** | **AI Advisory Triage** | `http://localhost/` (Tab: "AI Advisory Triage") | Incident selector, AI-generated summary, suggested category, suggested priority/severity, and non-binding advisory notice. |
| **SS-05** | **Grounded RAG & Citations** | `http://localhost/` (Tab: "AI Advisory Triage" -> "Run Grounded RAG") | Grounded diagnosis, resolution suggestion, confidence score (e.g. `88%`), and cited grounding sources (source badges, similarity score, snippets). |
| **SS-06** | **OpenAPI / Swagger UI** | `http://localhost:8080/swagger-ui.html` *(when enabled in dev)* | Complete interactive REST API catalog showing endpoints across Auth, Incidents, SLA, Notifications, KB, Analytics, and AI. |
| **SS-07** | **Spring Actuator Health Probe** | `http://localhost/actuator/health` or `curl /api/health` | Clean JSON health indicator showing application status `UP` and database connectivity. |
| **SS-08** | **Full Backend Test Run** | Terminal: `cd backend && mvn test` | Terminal output showing `Tests run: 282, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`. |
| **SS-09** | **Frontend Production Build** | Terminal: `cd frontend && npm run build` | Terminal output showing `tsc && vite build` bundled 1,634+ modules cleanly in ~15s. |
| **SS-10** | **Docker Compose Services** | Terminal: `docker compose ps` | Active multi-container topology showing `resolveai-postgres`, `resolveai-redis`, `resolveai-backend`, and `resolveai-frontend` all healthy. |

---

## 2. 10–15 Minute Technical Presentation & Interview Outline

This outline is structured for academic project defenses, portfolio reviews, and senior software engineering interviews.

### Slide 1: Title & Executive Overview (1 Minute)
- **Title**: *ResolveAI — AI-Powered Enterprise Incident & Service Management Platform*
- **Speaker Introduction**: Shanmukha Veeravalli
- **Core Pitch**: An enterprise-grade, ITIL-aligned incident management and service desk platform engineered with Java 21, Spring Boot 3, PostgreSQL 16 + pgvector, Redis 7, and React 18 with TypeScript.
- **Problem Statement**: Enterprise service desks face alert fatigue, delayed SLA response times, and scattered knowledge bases. ResolveAI introduces strict state machine governance augmented by an **evidence-grounded AI copilot** that accelerates diagnosis without hallucinating or mutating critical state.

---

### Slide 2: High-Level Architecture & Bounded Contexts (2 Minutes)
- **Architectural Style**: Modular Monolith.
- **Why Modular Monolith over Microservices?**
  - Strong transactional consistency across incident mutations, SLA timer records, and audit logs without distributed sagas or 2PC complexity.
  - Zero inter-service network latency for core operations.
  - Clean domain isolation via bounded contexts (`auth`, `user`, `team`, `incident`, `sla`, `notification`, `knowledge`, `analytics`, `ai`, `audit`, `common`).
- **Layered Pattern**: Strict `Controller` → `Service` → `Repository` → `Database` flow with validated DTO boundaries (zero JPA entity exposure).

---

### Slide 3: Core Workflow & Incident Lifecycle State Machine (2 Minutes)
- **ITIL State Machine**:
  - `NEW` → `TRIAGED` → `ASSIGNED` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`
  - Reopen and escalation loops: `RESOLVED` → `REOPENED` → `IN_PROGRESS`, `IN_PROGRESS` → `ESCALATED`
- **Invariant Rules**:
  - Invalid state jumps (e.g. `NEW` directly to `RESOLVED`) are rejected with `400 Bad Request`.
  - Every status or assignment modification appends an immutable `IncidentHistory` record.
  - Assignee mutations automatically generate in-app notifications.

---

### Slide 4: Automated SLA Engine & Operational Analytics (2 Minutes)
- **Configurable SLA Policies**: Dynamic deadline calculations for P1 through P4 priorities (response due dates, resolution due dates).
- **Breach Detection**: First acknowledgment stamps `responded_at`; resolution stamps `resolved_at`. Breaches trigger automated `SLA_BREACHED` notifications.
- **Push-Down Analytics**: Real-time MTTR, SLA compliance rates, and team workload queues computed directly inside PostgreSQL via optimized JPQL aggregations—no secondary reporting database or in-memory collection streaming required.

---

### Slide 5: The AI & RAG Differentiator (3 Minutes)
- **Core Engineering Principle: Human-in-the-Loop**:
  - AI is strictly advisory. Read-only transactions ensure the LLM **cannot** mutate ticket status, priority, or assignees in the database.
- **Phase 10: AI Advisory Foundation**:
  - Summarization, categorization suggestions, and severity recommendations.
- **Phase 11: Grounded RAG with `pgvector`**:
  - 1536-dimensional dense embeddings stored in PostgreSQL with HNSW cosine indexes.
  - Dual vector retrieval: searches published Knowledge Articles and resolved historical incidents.
  - Output includes confidence score, diagnostic hypothesis, recommended fix, and **strict source citations** (title, similarity score, excerpt).
- **Resilience**: If the AI provider is offline or unconfigured, the system returns an RFC 7807 `503 SERVICE_UNAVAILABLE` payload while core ticketing continues at 100% availability.

---

### Slide 6: Production Hardening, Security & Rate Limiting (2 Minutes)
- **Stateless Dual-Tier JWT**: 15-minute access tokens and 7-day refresh tokens with rotation.
- **Role-Based Access Control**: Granular method-level security (`@PreAuthorize`) separating `EMPLOYEE`, `ENGINEER`, `MANAGER`, and `ADMIN`.
- **Distributed Sliding-Window Rate Limiting**:
  - Redis-backed sliding window counter with graceful in-memory fallback.
  - Enforced on auth endpoints (15 req/60s on login, 10 on register) and AI endpoints (20 req/60s).
  - Returns `429 Too Many Requests` with `Retry-After: {seconds}` header.
- **Defense-in-Depth Security Headers**:
  - Spring Security and Nginx enforce `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, HSTS, Referrer-Policy, and Permissions-Policy.

---

### Slide 7: DevOps, Containerization & Automated Testing (2 Minutes)
- **Docker Compose Stack**:
  - 4 isolated services: Nginx (port 80), Spring Boot (internal port 8080), PostgreSQL 16 + pgvector, and Redis 7.
  - Backend port 8080 is not exposed to the host, preventing host port collisions.
- **GitHub Actions CI Pipeline**:
  - Parallel matrix jobs: `backend-ci` (Ubuntu, JDK 21), `frontend-ci` (Node 20), `docker-ci` (Docker build verification).
  - Concurrency control auto-cancels obsolete runs on branch updates.
- **Test Suite Integrity**:
  - **282 automated tests passing** (unit tests, security integration tests, caching tests, state machine tests, H2 vector tests).
  - 100% deterministic: Mock AI provider and in-memory cache eliminate external network dependencies during test runs.

---

### Slide 8: Limitations & Future Enhancements (1 Minute)
- **Current Architecture Scope**: Single-region modular monolith with PostgreSQL + Redis.
- **Real-World Considerations**:
  - Cloud deployment is container-ready (Docker images build cleanly), but production multi-region clustering and cloud secret vaults (e.g. AWS Secrets Manager / GCP Secret Manager) would be integrated upon live cloud deployment.
  - Future iterations could add WebSocket / SSE real-time notification streaming and asynchronous background batching for bulk embedding synchronization.

---

## 3. Likely Interview Q&A Defense

### Q1: Why did you choose a Modular Monolith instead of Microservices?
> **Answer**: In incident management, ticket status changes, SLA timestamp tracking, and audit logging must occur within ACID database transactions. A microservices architecture would require distributed saga coordinators or two-phase commits, introducing failure modes and inter-service network hops. By organizing the application into strictly bounded contexts (`auth`, `incident`, `sla`, `knowledge`, `analytics`), we retain modularity, fast compilation, and simple deployment while preserving strong relational integrity.

### Q2: How do you prevent LLM hallucinations from corrupting production tickets?
> **Answer**: Two strict architectural safeguards:
> 1. **Zero Auto-Mutation**: The AI analysis endpoints are `@Transactional(readOnly = true)`. Repository `save()` is never called. Recommendations are purely advisory and presented to the human engineer.
> 2. **Grounded RAG with Citations**: The prompt is grounded using top-K vector search over published knowledge articles and historical tickets retrieved via `pgvector`. Every recommendation must cite the exact source IDs, titles, and excerpts it was generated from.

### Q3: How does the system handle an external AI outage or rate limit exhaustion?
> **Answer**: Through strict failure isolation. Calls to `AiProvider` are wrapped in timeouts and exception handlers that throw `AiServiceUnavailableException`. The global exception handler converts this to an HTTP 503 response. Core incident creation, status transitions, SLA calculations, and analytics do not depend on the AI subsystem and continue operating without degradation. Furthermore, incoming requests to AI endpoints are protected by our Redis sliding-window rate limiter (20 requests per 60 seconds) to prevent quota exhaustion.
