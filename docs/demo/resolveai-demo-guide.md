# ResolveAI — End-to-End Enterprise Demonstration Guide

This guide provides a repeatable, step-by-step demonstration sequence for evaluating, presenting, and interviewing on the **ResolveAI** platform.

Every step is verified against the real codebase and demonstrates production architectural capabilities: modular monolith boundaries, strict ITIL incident lifecycles, automated SLA tracking, role-based access control (RBAC), distributed sliding-window rate limiting, and grounded Retrieval-Augmented Generation (RAG) with vector citations.

---

## Demonstration Prerequisites

Before commencing the demonstration, ensure the following environment is configured:

1. **Software Requirements**:
   - Docker & Docker Compose (v2.20+) **OR** Java 21 JDK + Node.js 20+
   - Terminal (PowerShell or Bash)
   - Web Browser (Chrome, Firefox, Edge) or REST Client (cURL, Postman, or Swagger UI)
2. **Environment Configuration**:
   - Working `.env` file copied from `.env.example`
   - Active profile: `dev` (local development) or `prod` (containerized)
   - AI Provider: For offline demo, `AI_PROVIDER=mock` and `AI_ENABLED=true` (or configure an OpenAI-compatible API key for live LLM completions)
3. **Seeded System Data**:
   - The database automatically contains Flyway-seeded data:
     - 4 Roles: `EMPLOYEE`, `ENGINEER`, `MANAGER`, `ADMIN`
     - 5 ITIL Categories: Hardware, Network, Software, Identity/Access, Security
     - 4 SLA Policies: P1 (15m/120m), P2 (30m/480m), P3 (120m/1440m), P4 (240m/4320m)

---

## Repeatable 14-Step Demonstration Sequence

---

### Step 1: Start the Platform & Verify Container Health

- **What to Do**:
  Start the multi-container stack in the project root:
  ```bash
  docker compose up --build -d
  docker compose ps
  ```
  *(Or for local development: run `cd backend && mvn spring-boot:run` in one terminal and `cd frontend && npm run dev` in another).*
- **What Should Happen**:
  All 4 services enter a healthy status:
  - `resolveai-postgres` (pgvector on port 5432)
  - `resolveai-redis` (port 6379)
  - `resolveai-backend` (internal port 8080)
  - `resolveai-frontend` (Nginx reverse proxy on port 80)
  
  Verify the public health probe:
  ```bash
  curl -s http://localhost/api/health
  # Returns: {"status":"UP","message":"ResolveAI API is operational",...}
  ```
- **What Concept to Explain**:
  - Multi-container architecture with network segregation: frontend serves React SPA and reverse-proxies `/api/*` requests internally to Spring Boot.
  - The backend port 8080 is not exposed directly on the host, preventing host conflicts with services such as Jenkins or local dev servers.
  - Health checks provide liveness probes for container orchestration.

---

### Step 2: User Registration & Authentication (JWT Dual-Tier Tokens)

- **What to Do**:
  Register a demonstration user and authenticate to obtain JWT tokens:
  ```bash
  # Register demo user
  curl -X POST http://localhost/api/auth/register \
    -H "Content-Type: application/json" \
    -d '{
      "email": "sarah.connor@enterprise.com",
      "password": "SecurePassword123!",
      "firstName": "Sarah",
      "lastName": "Connor"
    }'

  # Login to obtain tokens
  curl -X POST http://localhost/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{
      "email": "sarah.connor@enterprise.com",
      "password": "SecurePassword123!"
    }'
  ```
- **What Should Happen**:
  - Registration returns `201 Created` with default `EMPLOYEE` role.
  - Login returns `200 OK` with JSON payload containing:
    - `accessToken` (15-minute validity, HMAC-SHA256 signature)
    - `refreshToken` (7-day validity, database rotation enabled)
    - User profile details
- **What Concept to Explain**:
  - Dual-tier stateless JWT architecture: short-lived access tokens minimize exposure window; refresh tokens support seamless session renewal without re-prompting for credentials.
  - Password hashing using BCrypt with salt cost factor 12.
  - Client Axios interceptors automatically attach the `Authorization: Bearer <token>` header to all outgoing requests.

---

### Step 3: Create an Incident (ITIL Ticket Reporting)

- **What to Do**:
  Submit a new technical incident as the authenticated employee:
  ```bash
  curl -X POST http://localhost/api/incidents \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "title": "VPN Gateway Timeout during EU peak hours",
      "description": "Remote engineering staff experiencing 504 gateway timeouts when connecting to the Frankfurt VPN concentrator.",
      "categoryId": 2,
      "priority": "P2",
      "severity": "HIGH"
    }'
  ```
- **What Should Happen**:
  Returns `201 Created` with:
  - Formatted human-readable ticket number (e.g. `INC-20261012-001`)
  - Initial state: `NEW`
  - Reporter set automatically from the JWT principal
  - Automatic `SLA_RECORD` generated linking to the active P2 policy
  - An in-app `INCIDENT_CREATED` notification dispatched to the reporter
- **What Concept to Explain**:
  - Separation of internal surrogate primary keys (`BIGINT`) from high-entropy human-facing ticket identifiers (`INC-YYYYMMDD-XXX`).
  - Transactional boundary: creating an incident atomically initializes its SLA record and dispatches notifications via Spring `ApplicationEventPublisher`.

---

### Step 4: Explain Incident Lifecycle & State Machine Constraints

- **What to Do**:
  Demonstrate state machine enforcement by attempting an illegal transition (e.g., jumping from `NEW` directly to `RESOLVED` without triage or investigation):
  ```bash
  curl -X PATCH http://localhost/api/incidents/1/status \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "targetStatus": "RESOLVED",
      "notes": "Trying to resolve immediately"
    }'
  ```
- **What Should Happen**:
  Returns `400 Bad Request` with an RFC 7807 compliant error envelope:
  ```json
  {
    "status": 400,
    "error": "INVALID_STATE_TRANSITION",
    "message": "Cannot transition incident from NEW directly to RESOLVED"
  }
  ```
- **What Concept to Explain**:
  - Enforced ITIL state machine: `NEW` → `TRIAGED` → `ASSIGNED` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`.
  - Illegal transitions are rejected deterministically by the domain service layer, preventing ticket corruption or bypassed resolution reviews.

---

### Step 5: Demonstrate AI Advisory Analysis (Phase 10 Foundation)

- **What to Do**:
  Execute AI triage analysis on the reported incident:
  ```bash
  curl -X POST http://localhost/api/ai/incidents/1/analyze \
    -H "Authorization: Bearer $ENGINEER_TOKEN"
  ```
  *(Or navigate to the **AI Advisory Triage** tab in the frontend web application).*
- **What Should Happen**:
  Returns `200 OK` with structured advisory suggestions:
  ```json
  {
    "incidentId": 1,
    "suggestedCategory": "Network & Connectivity",
    "suggestedPriority": "P2",
    "suggestedSeverity": "HIGH",
    "summary": "Frankfurt VPN concentrator gateway timeouts impacting remote EU staff.",
    "analysis": "1. Check IPsec tunnel capacity on Frankfurt gateway.\n2. Inspect CPU utilization on concentrator cluster.\n3. Temporarily route traffic through Amsterdam gateway."
  }
  ```
- **What Concept to Explain**:
  - **Zero Auto-Mutation**: The endpoint is strictly read-only (`@Transactional(readOnly = true)`). AI recommendations do not mutate database rows; human approval is required.
  - **Failure Isolation**: If AI is offline, returns `503 Service Unavailable`, but core ticketing operations remain fully functional.

---

### Step 6: Assign Incident to Team & Engineer

- **What to Do**:
  As a manager or admin, route the ticket to the Network Reliability Squad:
  ```bash
  curl -X PATCH http://localhost/api/incidents/1/assign \
    -H "Authorization: Bearer $MANAGER_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "teamId": 1,
      "assigneeId": 2,
      "note": "Assigned to Network Squad for immediate investigation."
    }'
  ```
- **What Should Happen**:
  - Status automatically transitions from `NEW` / `TRIAGED` to `ASSIGNED`.
  - An immutable `IncidentHistory` record is written logging the assignment mutation.
  - An in-app `INCIDENT_ASSIGNED` notification is dispatched to engineer ID 2.
- **What Concept to Explain**:
  - Organizational team queues and roster management.
  - Real-time audit history: all assignment and priority changes record the actor, timestamp, old value, and new value.

---

### Step 7: Demonstrate SLA Calculation & Breach Engine

- **What to Do**:
  Inspect the SLA record associated with the incident:
  ```bash
  curl -s http://localhost/api/sla/incidents/1 \
    -H "Authorization: Bearer $ACCESS_TOKEN"
  ```
- **What Should Happen**:
  Returns `200 OK` with calculated timestamps:
  - `responseDueAt` = `createdAt` + 30 minutes (P2 target)
  - `resolutionDueAt` = `createdAt` + 8 hours (P2 target)
  - When the engineer transitions the ticket to `IN_PROGRESS`, `respondedAt` is stamped.
  - If `respondedAt > responseDueAt`, `isResponseBreached` is flagged `true` and an `SLA_BREACHED` notification is triggered.
- **What Concept to Explain**:
  - SLAs are dynamic and configurable via the `sla_policies` table rather than hardcoded in application logic.
  - Supports ITIL metrics: First Response Time (FRT), Mean Time to Acknowledge (MTTA), and Mean Time to Resolve (MTTR).

---

### Step 8: Comments, Immutable History & Notifications

- **What to Do**:
  Add an internal engineer note and review the ticket history and notification feed:
  ```bash
  # Add internal diagnosis note (hidden from customer employee)
  curl -X POST http://localhost/api/incidents/1/comments \
    -H "Authorization: Bearer $ENGINEER_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "commentText": "BGP route flap detected on upstream provider AS1234. Failover in progress.",
      "isInternal": true
    }'

  # Retrieve notification count
  curl -s http://localhost/api/notifications/unread-count \
    -H "Authorization: Bearer $ENGINEER_TOKEN"
  ```
- **What Should Happen**:
  - Comments support privacy separation: internal notes are visible only to engineers, managers, and admins.
  - Unread notification count increments.
  - Incident history retains an immutable chronological log of every status and comment event.
- **What Concept to Explain**:
  - ITIL separation of customer-facing communication vs. private engineering investigation notes.
  - Push-down notification queries with atomic `PATCH /api/notifications/read-all` bulk updates.

---

### Step 9: Knowledge Base Authoring & Article Linking

- **What to Do**:
  Publish a Knowledge Article and link it to the incident:
  ```bash
  # Create draft KB article
  curl -X POST http://localhost/api/knowledge/articles \
    -H "Authorization: Bearer $ENGINEER_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "title": "Frankfurt VPN Concentrator BGP Failover Procedure",
      "problem": "504 Gateway Timeouts on Frankfurt Concentrator",
      "symptoms": "Clients disconnect; packet loss exceeds 40%",
      "rootCause": "Upstream route flapping on primary transit provider",
      "resolution": "Execute automated routing shift to secondary tunnel and reset IPsec SA.",
      "categoryId": 2,
      "tags": "vpn,network,bgp,frankfurt"
    }'

  # Publish article (DRAFT -> PUBLISHED)
  curl -X PATCH http://localhost/api/knowledge/articles/1/publish \
    -H "Authorization: Bearer $ENGINEER_TOKEN"

  # Link article to incident
  curl -X POST http://localhost/api/incidents/1/knowledge/1 \
    -H "Authorization: Bearer $ENGINEER_TOKEN"
  ```
- **What Should Happen**:
  - Article status transitions from `DRAFT` to `PUBLISHED` with `publishedAt` timestamp.
  - Linking creates an entry in `incident_knowledge_links`. Duplicate links return `409 Conflict`.
- **What Concept to Explain**:
  - Knowledge Base lifecycle: `DRAFT` → `PUBLISHED` → `ARCHIVED`.
  - Cache authorization safety: only `PUBLISHED` articles are cached in Redis. Draft articles always execute fresh database RBAC checks so unauthorized callers cannot inspect drafts.

---

### Step 10: Demonstrate Grounded RAG Analysis (Phase 11 Vector Pipeline)

- **What to Do**:
  Trigger evidence-grounded RAG analysis on the incident:
  ```bash
  curl -X POST http://localhost/api/ai/incidents/1/rag-analyze \
    -H "Authorization: Bearer $ENGINEER_TOKEN"
  ```
  *(Or use the **Grounded RAG Advisory** button on the frontend AI Triage page).*
- **What Should Happen**:
  Returns `200 OK` with diagnosis, resolution suggestion, confidence score, and cited sources:
  ```json
  {
    "incidentId": 1,
    "diagnosis": "VPN gateway timeout caused by upstream BGP route instability on Frankfurt concentrator.",
    "resolutionSuggestion": "Shift traffic to secondary tunnel and reset IPsec Security Associations per procedure in KB-1.",
    "confidence": 0.91,
    "sources": [
      {
        "sourceType": "KNOWLEDGE_ARTICLE",
        "sourceId": 1,
        "title": "Frankfurt VPN Concentrator BGP Failover Procedure",
        "similarity": 0.942,
        "reference": "KB-1",
        "snippet": "Execute automated routing shift to secondary tunnel and reset IPsec SA."
      }
    ]
  }
  ```
- **What Concept to Explain**:
  - Real `pgvector` HNSW index cosine distance matching (`<=>` operator) over 1536-dimensional dense vectors.
  - Grounded prompt engineering: the LLM is constrained to cite specific sources, eliminating hallucinations.
  - Visibility filters: Draft articles and non-resolved tickets are strictly excluded from RAG retrieval.

---

### Step 11: Real-Time Operational Analytics Dashboard

- **What to Do**:
  Navigate to the **Operations Analytics** tab in the web frontend (`http://localhost/` or `http://localhost:5173/`):
  *(Or invoke `/api/analytics/overview` and `/api/analytics/sla` via REST).*
- **What Should Happen**:
  Displays:
  - Total incident count, open incidents, resolved/closed tickets, and critical active P1 tickets.
  - Mean Time to Resolve (MTTR) calculated across resolved incidents.
  - SLA Compliance percentage rate and breach counts.
  - Workload distribution across engineering teams.
  - Status, Priority, and Severity distribution charts.
- **What Concept to Explain**:
  - Zero separate analytics database: all KPIs are computed directly in PostgreSQL using optimized JPQL push-down aggregations (`COUNT`, `SUM(CASE WHEN...)`, `AVG`).
  - Management authorization: analytics endpoints are restricted to `MANAGER` and `ADMIN` roles.

---

### Step 12: Role-Based Access Control & Distributed Rate Limiting

- **What to Do**:
  Demonstrate defense-in-depth security:
  1. **RBAC Endpoint Check**:
     Attempt to access the admin verification endpoint as an employee:
     ```bash
     curl -s -o /dev/null -w "%{http_code}\n" http://localhost/api/rbac/admin \
       -H "Authorization: Bearer $EMPLOYEE_TOKEN"
     # Returns 403 Forbidden
     ```
  2. **Rate Limiting Check**:
     Send more than 15 requests to the login endpoint within 60 seconds:
     ```bash
     for i in {1..20}; do
       curl -s -o /dev/null -w "%{http_code}\n" http://localhost/api/auth/login \
         -H "Content-Type: application/json" \
         -d '{"email":"test@enterprise.com","password":"wrong"}'
     done
     # Returns 429 Too Many Requests after 15 attempts, with Retry-After header
     ```
- **What Concept to Explain**:
  - Spring Security method-level authorization (`@PreAuthorize`) enforcing clear separation between `401 Unauthorized` (missing/invalid token) and `403 Forbidden` (insufficient role).
  - Rate limiting enforced via `RateLimitInterceptor` with Redis sliding-window counters and automatic in-memory fallback.

---

### Step 13: Container Architecture & Network Segregation

- **What to Do**:
  Inspect Docker container processes and network configuration:
  ```bash
  docker compose ps
  docker network inspect resolveai-network
  ```
- **What Should Happen**:
  - All 4 containers share the private bridge network `resolveai-network`.
  - Only frontend port `80` (and development database ports in dev profile) are bound to the host.
  - Spring Boot backend port `8080` is internal only, insulated behind Nginx.
- **What Concept to Explain**:
  - Multi-stage Dockerfiles optimizing image size and vulnerability surface.
  - Least-privilege container execution: backend runs as non-root user `spring`.
  - Persistent volume isolation for PostgreSQL (`postgres_data`) and Redis (`redis_data`).

---

### Step 14: Continuous Integration & Automated Test Harness

- **What to Do**:
  Review or execute the automated verification suite:
  ```bash
  # Execute all 282 backend tests
  cd backend && mvn test

  # Execute frontend typecheck and production bundling
  cd ../frontend && npm run build
  ```
- **What Should Happen**:
  - Backend: **282 tests pass** with 0 failures, 0 errors, and 0 skipped.
  - Frontend: **TypeScript compiler + Vite** bundling builds cleanly in ~15 seconds without lint or type errors.
- **What Concept to Explain**:
  - GitHub Actions CI matrix: `backend-ci` (Ubuntu, JDK 21), `frontend-ci` (Ubuntu, Node 20), and `docker-ci` (Docker compose validation).
  - Test isolation: H2 in-memory PostgreSQL compatibility mode and Mock AI providers ensure deterministic, fast CI execution without external network calls or cloud costs.
