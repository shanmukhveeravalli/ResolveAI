# ResolveAI — Cloud Deployment Readiness Guide (Phase 16)

## Overview

ResolveAI is containerized via Docker and can be deployed to any cloud provider that supports Docker container workloads. This guide covers the recommended architecture, required environment variables, deployment process, and verification checklist.

---

## Recommended Architecture

```
                          [ Internet / CDN ]
                                  |
                        [ HTTPS Reverse Proxy ]
                        (e.g. Nginx / Traefik /
                         Cloud Load Balancer)
                                  |
                   ┌──────────────┴──────────────┐
                   │                             │
             [ Frontend ]               [ Backend API ]
         Nginx (port 80/443)         Spring Boot (port 8080)
         React SPA + /api proxy          (internal only)
                                              |
                          ┌───────────────────┴──────────────────┐
                          │                                      │
                  [ PostgreSQL 16 ]                         [ Redis 7 ]
                  pgvector extension                    Cache & rate limiter
                  Managed DB service                    Managed cache service
```

### Key Networking Rules
- **Frontend (port 80)**: Publicly accessible via HTTPS reverse proxy.
- **Backend (port 8080)**: Internal network only; reachable via frontend Nginx `/api/` proxy and direct healthcheck.
- **PostgreSQL (port 5432)**: Internal/private network only — never expose publicly.
- **Redis (port 6379)**: Internal/private network only — never expose publicly.

---

## Required Cloud Services

| Service | Recommended Providers | Requirement |
| :--- | :--- | :--- |
| **Container Runtime** | AWS ECS / GCP Cloud Run / Azure Container Apps / Render / Fly.io | Docker image execution |
| **PostgreSQL 16+** | AWS RDS, GCP Cloud SQL, Supabase, Neon, Railway | Must support `pgvector` extension (required for RAG/embeddings) |
| **Redis 7+** | AWS ElastiCache, GCP Memorystore, Upstash, Redis Cloud | AOF persistence recommended for rate-limiting state |
| **Secrets Management** | AWS Secrets Manager, GCP Secret Manager, HashiCorp Vault, platform env vars | All credentials injected at runtime |
| **Persistent Storage** | Managed volumes or cloud object storage | PostgreSQL `pg_data` and Redis `aof` persistence |
| **HTTPS / Domain** | Cloudflare, AWS ALB, GCP Cloud Load Balancer | TLS termination in front of the stack |

---

## Required Environment Variables (Production)

### Mandatory — Application will fail to start without these

| Variable | Description | Example |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Must be `prod` in production | `prod` |
| `DB_HOST` | PostgreSQL hostname | `db.example.com` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `resolveai_db` |
| `DB_USERNAME` | Database user | `resolveai_app` |
| `DB_PASSWORD` | Database password | *(secret manager)* |
| `REDIS_HOST` | Redis hostname | `redis.example.com` |
| `JWT_SECRET` | 256-bit JWT signing secret (base64) | `openssl rand -base64 32` |
| `CORS_ALLOWED_ORIGINS` | Exact frontend origin (no trailing slash) | `https://yourdomain.com` |

### Optional / Feature Flags

| Variable | Default | Description |
| :--- | :--- | :--- |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_SSL_MODE` | `prefer` | SSL mode (`require` for production) |
| `DB_POOL_MAX_SIZE` | `20` | HikariCP max connections |
| `DB_POOL_MIN_IDLE` | `5` | HikariCP min idle connections |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(empty)* | Redis password if auth enabled |
| `REDIS_SSL_ENABLED` | `false` | Enable Redis TLS (`true` for managed cloud Redis) |
| `REDIS_TIMEOUT` | `2000ms` | Redis command timeout |
| `FLYWAY_ENABLED` | `true` | Run Flyway migrations on startup |
| `JWT_ACCESS_EXPIRATION` | `900000` | Access token TTL (ms, 15 min) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh token TTL (ms, 7 days) |
| `AI_ENABLED` | `false` | Enable AI provider integrations |
| `AI_PROVIDER` | `mock` | AI provider: `openai` / `mock` |
| `AI_API_KEY` | *(empty)* | OpenAI API key |
| `AI_MODEL` | `gpt-4o-mini` | LLM model name |
| `AI_EMBEDDING_MODEL` | `text-embedding-3-small` | Embedding model name |
| `AI_EMBEDDING_DIMENSIONS` | `1536` | Embedding vector dimensions |
| `AI_TIMEOUT` | `10` | AI provider request timeout (seconds) |
| `AI_TOP_K` | `5` | Number of similar results for RAG |
| `AI_BASE_URL` | `https://api.openai.com/v1` | AI API base URL |
| `SWAGGER_ENABLED` | `false` | Enable Swagger UI in prod (default off) |
| `ACTUATOR_HEALTH_SHOW_DETAILS` | `when_authorized` | Spring Actuator health detail level |
| `SERVER_PORT` | `8080` | Backend server port |
| `SHUTDOWN_TIMEOUT` | `30s` | Graceful shutdown phase timeout |
| `FRONTEND_PORT` | `80` | Host port for Nginx (Docker Compose) |
| `LOG_LEVEL` | `INFO` | Root log level: `DEBUG`/`INFO`/`WARN`/`ERROR` |
| `APP_LOG_LEVEL` | `INFO` | `com.resolveai` package log level |

---

## PostgreSQL + pgvector Requirement

> [!IMPORTANT]
> ResolveAI Phase 11–12 (RAG & Vector Embeddings) requires the **pgvector** extension.
> Standard PostgreSQL 16 images do **not** include pgvector by default.
>
> **Docker**: The compose file uses `pgvector/pgvector:pg16` which includes pgvector pre-installed.
>
> **Managed Cloud Services**: Verify pgvector support **before selecting** your provider:
> - **AWS RDS**: pgvector available on PostgreSQL 15+ (enable via `CREATE EXTENSION vector`)
> - **GCP Cloud SQL**: pgvector available (enable via Cloud SQL flag)
> - **Supabase**: pgvector included by default ✅
> - **Neon**: pgvector included by default ✅
> - **Azure Database for PostgreSQL**: pgvector available (flexible server, 14+)
>
> Flyway migration `V3__add_vector_embeddings.sql` creates the extension and HNSW indexes automatically on first run.

---

## Docker Deployment Process

### 1. Prepare Environment

```bash
# Copy the template and fill in production values
cp .env.example .env.prod
# Edit .env.prod with real production credentials — NEVER commit this file
```

> [!NOTE]
> A `docker-compose.override.yml` is included for **local development convenience**.
> It is automatically loaded by `docker compose` for local dev (`docker compose up`).
> For production, explicitly exclude the override to avoid dev defaults leaking:
> ```bash
> docker compose -f docker-compose.yml --env-file .env.prod up -d
> ```

### 2. Build Production Docker Images

```bash
# Build both images from the repository root (for production, exclude override)
docker compose -f docker-compose.yml build

# Or build individually with version tags for a registry push
docker build -t yourregistry/resolveai-backend:1.0.0 -f backend/Dockerfile .
docker build -t yourregistry/resolveai-frontend:1.0.0 -f frontend/Dockerfile ./frontend
```

### 3. Start the Stack

```bash
# Start with production env file (explicitly excludes docker-compose.override.yml)
docker compose -f docker-compose.yml --env-file .env.prod up -d

# Check all containers are healthy
docker compose ps
```


### 4. Verify Health Checks

```bash
# PostgreSQL health
docker exec resolveai-postgres pg_isready -U $DB_USERNAME -d $DB_NAME

# Redis health
docker exec resolveai-redis redis-cli ping

# Backend health (internal)
docker exec resolveai-backend curl -sf http://localhost:8080/api/health

# Backend Spring Actuator liveness & readiness (internal)
docker exec resolveai-backend curl -sf http://localhost:8080/actuator/health/liveness
docker exec resolveai-backend curl -sf http://localhost:8080/actuator/health/readiness

# Frontend health (public)
curl -sf http://localhost/healthz

# End-to-end API via Nginx proxy (public)
curl -sf http://localhost/api/health
```

---

## Health Check Endpoints Reference

| Endpoint | Access | Purpose |
| :--- | :--- | :--- |
| `GET /api/health` | Public | Application-level health (custom controller) |
| `GET /actuator/health` | Public | Spring Boot Actuator full health |
| `GET /actuator/health/liveness` | Public | Kubernetes/ECS liveness probe |
| `GET /actuator/health/readiness` | Public | Kubernetes/ECS readiness probe |
| `GET /actuator/info` | Public | App version info |
| `GET /actuator/**` | `ADMIN` role | All other actuator endpoints (secured) |
| `GET /healthz` | Public | Frontend/Nginx health check |

---

## Persistent Storage Requirements

| Service | Volume Mount | Persistence Requirement |
| :--- | :--- | :--- |
| **PostgreSQL** | `/var/lib/postgresql/data` | **Critical** — must persist across restarts |
| **Redis** | `/data` | Recommended — AOF journal for rate limiter state |

Use cloud-managed block storage (AWS EBS, GCP Persistent Disk) or managed database services (preferred).

---

## HTTPS / Domain Configuration

ResolveAI Nginx is configured to serve on HTTP port 80. TLS termination should be handled by an upstream component:

1. **Cloud Load Balancer** (AWS ALB, GCP HTTPS LB): Terminate HTTPS at the load balancer, forward to port 80.
2. **Reverse Proxy** (Nginx, Traefik, Caddy): Run as a sidecar or separate service with a TLS certificate.
3. **Platform-Managed TLS** (Render, Fly.io, Railway): Automatic HTTPS on custom domains.

The frontend Nginx configuration includes `proxy_set_header X-Forwarded-Proto $scheme` and the backend Spring Boot base `application.yml` includes `forward-headers-strategy: framework` (via `application-prod.yml`) to correctly detect HTTPS behind a proxy.

---

## Deployment Verification Checklist

Before going live, verify each of the following:

- [ ] `SPRING_PROFILES_ACTIVE=prod` is set
- [ ] All mandatory environment variables are configured (see table above)
- [ ] No default passwords or example secrets remain in production env
- [ ] `DB_SSL_MODE=require` for managed PostgreSQL
- [ ] `REDIS_SSL_ENABLED=true` for managed Redis (if provider supports TLS)
- [ ] `CORS_ALLOWED_ORIGINS` is set to exact production frontend URL (no wildcards)
- [ ] `SWAGGER_ENABLED=false` (or removed from public access)
- [ ] Actuator endpoints other than `/health` and `/info` are not publicly reachable
- [ ] PostgreSQL and Redis are NOT exposed on public network interfaces
- [ ] Backend port 8080 is NOT exposed publicly (only frontend port 80/443 is)
- [ ] HTTPS is enabled for all public endpoints
- [ ] `docker compose ps` shows all 4 containers as `(healthy)`
- [ ] `GET /api/health` returns `{"status":"UP",...}`
- [ ] `GET /actuator/health/liveness` returns `{"status":"UP"}`
- [ ] `GET /actuator/health/readiness` returns `{"status":"UP"}`
- [ ] Flyway migrations report no errors in backend startup logs
- [ ] pgvector extension is available (`SELECT * FROM pg_available_extensions WHERE name = 'vector'`)

---

## Shutdown & Rollback

### Graceful Shutdown
The backend is configured with `server.shutdown: graceful` and a 30-second shutdown timeout (`SHUTDOWN_TIMEOUT`). In-flight requests will be allowed to complete before the JVM exits, preventing request drops during rolling restarts.

```bash
# Graceful stop (containers finish in-flight requests)
docker compose stop

# Hard stop (immediate — not recommended in production)
docker compose kill
```

### Rolling Update
```bash
# Rebuild and restart only the updated service
docker compose up -d --build backend
# Docker will wait for health check before routing traffic (depends_on: healthy)
```

### Rollback
```bash
# Revert to previous image tag
docker compose down backend
docker tag resolveai-backend:previous resolveai-backend:0.1.0
docker compose up -d backend
```

### Database Rollback
Flyway versioned migrations are append-only. To rollback a schema change, create a new **undo migration** (`V4__revert_...sql`) rather than modifying existing migration files.

---

## CI/CD Integration — Next Steps

The GitHub Actions workflow in `.github/workflows/ci.yml` currently validates builds and tests. For full CD, the following steps would be added in a future phase:
1. Push tagged Docker images to a container registry (e.g. GHCR, ECR, Docker Hub).
2. Trigger a rolling deploy to the target cloud service using the registry image.
3. Run smoke/health checks post-deployment as a final CD gate.

No registry push or automated deployment has been configured in Phase 16 — this is intentional to keep credentials out of CI until the deployment target is finalized.
