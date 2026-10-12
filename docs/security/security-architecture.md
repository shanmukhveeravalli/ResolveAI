# ResolveAI — Production Security Architecture & Hardening Guide (Phase 17)

## Overview

ResolveAI employs defense-in-depth security principles across transport, authentication, role-based authorization, resource isolation, API abuse prevention, caching integrity, and container infrastructure.

---

## 1. Authentication & JWT Security

- **Stateless Authentication**: Pure token-based authentication using HMAC-SHA256 signatures with 256-bit keys (`io.jsonwebtoken` JJWT 0.12.6).
- **Token Type Discrimination**:
  - `ACCESS` tokens (15-minute expiration default) contain user identity, email, and role claim.
  - `REFRESH` tokens (7-day expiration default) are dedicated exclusively to the `/api/auth/refresh` endpoint and rejected if used against protected business APIs.
  - Access tokens cannot be used to refresh tokens.
- **Production Secret Validation**: In the `prod` profile, `JWT_SECRET` is mandatory and cannot fall back to insecure defaults. The signing key parser validates that secret keys are non-null and non-blank with minimum 256-bit entropy.
- **Password Protection**: Passwords are saved as BCrypt hashes (strength 12) with zero plaintext retention.

---

## 2. Role-Based Access Control (RBAC) & Resource Authorization

ResolveAI enforces a two-tier authorization model:

### Tier 1: Role-Level Access (Method & Path Security)

| Role | Permissions Overview |
| :--- | :--- |
| **EMPLOYEE** | Create incidents, view own incidents, comment on accessible incidents, search published KB articles, view own notifications. |
| **ENGINEER** | View team/assigned incidents, update incident status, author draft/published KB articles, execute AI and grounded RAG triage. |
| **MANAGER** | Full team management, assign incidents, modify any incident, manage knowledge base lifecycle, view operational analytics dashboards. |
| **ADMIN** | Full system administration, user management, system audit logs, actuator administrative endpoints. |

### Tier 2: Resource-Level Authorization (`AuthorizationService`)

- **Incident Scoping**: Employees are strictly scoped to incidents where `reporter_id = caller_id`. Engineers access assigned or team incidents.
- **Internal Note Isolation**: Comments marked `isInternal=true` are automatically filtered out from responses to `EMPLOYEE` callers.
- **Knowledge Article Isolation**: `DRAFT` and `ARCHIVED` articles are strictly invisible to `EMPLOYEE` callers and only accessible to authors or `MANAGER`/`ADMIN`.
- **Authorization-Safe Caching**: Only `PUBLISHED` articles are cached in Redis/in-memory caches (`unless = "#result.status.name() != 'PUBLISHED'"`), guaranteeing that `DRAFT` articles always trigger ownership evaluation.

---

## 3. API Abuse Resistance & Rate Limiting

Sensitive endpoints are protected against credential stuffing, token refresh flooding, and LLM quota exhaustion via `@RateLimit`:

| Endpoint | Bucket Key | Limit | Window | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `POST /api/auth/login` | `auth_login:{ip}` | 15 req | 60s | Brute force / credential stuffing prevention |
| `POST /api/auth/register` | `auth_register:{ip}` | 10 req | 60s | Automated account creation prevention |
| `POST /api/auth/refresh` | `auth_refresh:{ip}` | 30 req | 60s | Token refresh flooding protection |
| `POST /api/ai/incidents/{id}/analyze` | `ai_analyze:{user/ip}` | 20 req | 60s | LLM quota & compute protection |
| `POST /api/ai/incidents/{id}/rag-analyze` | `ai_rag:{user/ip}` | 20 req | 60s | Vector search & LLM quota protection |

**Resilient Implementation**:
- Uses distributed atomic Redis counters (`INCR` + `EXPIRE`) when Redis is connected.
- Automatically falls back to high-performance, thread-safe in-memory sliding windows if Redis is unavailable or during testing.
- Exceeding the rate limit returns `HTTP 429 Too Many Requests` with a `Retry-After` header and RFC-compliant `ApiErrorResponse`.

---

## 4. Security Headers & Transport Hardening

Both the backend Spring Security filter chain and the frontend Nginx reverse proxy enforce defensive headers:

- `X-Frame-Options: DENY` (prevents clickjacking attacks)
- `X-Content-Type-Options: nosniff` (prevents MIME-type sniffing)
- `X-XSS-Protection: 1; mode=block` (browser XSS filtering)
- `Referrer-Policy: strict-origin-when-cross-origin` (prevents leakage of full URL query parameters)
- `Permissions-Policy: camera=(), microphone=(), geolocation=()` (restricts unnecessary browser capabilities)
- `Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self' http: https: ws: wss:; frame-ancestors 'none';` (SPA-safe CSP)
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` (enforces HTTPS)
- `server_tokens off` (hides Nginx server versions from attackers)

---

## 5. AI & RAG Security

- **Failure Isolation**: Network failures, timeouts, or malformed JSON from external AI providers throw `AiServiceUnavailableException` (HTTP 503) without affecting database transactions.
- **Strict Non-Mutation**: AI endpoints are strictly advisory (`readOnly = true`); no database entity status or assignment is automatically altered by an AI response.
- **Grounding Privacy**: Vector search for candidate RAG sources validates `authorizationService.canViewKnowledgeArticle(article)` before sending content to prompts.
- **Credential Hygiene**: API keys and authorization headers are never logged.

---

## 6. Container & Network Security

- **Non-Root Runtime**: Backend Docker container runs as non-privileged user `appuser` (UID 100).
- **Network Isolation**: Backend (port 8080), PostgreSQL (port 5432), and Redis (port 6379) communicate across the private internal Docker bridge network (`resolveai-network`).
- **Internal Only Backend**: Port 8080 is not mapped to host interfaces in production; only Frontend Nginx (port 80) is published.
