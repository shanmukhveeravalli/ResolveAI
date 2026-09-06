# ResolveAI — REST API Specification & Plan

## 1. REST Conventions & Standards

- **Base URL**: `/api`
- **Payload Format**: JSON (`Content-Type: application/json; charset=UTF-8`)
- **Authentication**: Stateless JWT token sent via `Authorization: Bearer <jwt-token>` header.
- **Character Encoding**: UTF-8
- **Date/Time Standard**: ISO 8601 UTC strings (e.g. `2026-09-05T13:30:00Z`).

### HTTP Status Code Usage

| Status Code | Meaning | Usage Scenario |
| :--- | :--- | :--- |
| `200 OK` | Successful request | Successful GET, PUT, or PATCH operation. |
| `201 Created` | Resource created | Successful POST creating a new resource. |
| `204 No Content`| Success without body | Successful DELETE or empty action. |
| `400 Bad Request` | Request payload invalid | Business logic validation failure, illegal state transition. |
| `401 Unauthorized`| Authentication missing | Missing, expired, or malformed JWT token. |
| `403 Forbidden` | Access denied | User authenticated but lacks required role or resource ownership. |
| `404 Not Found` | Resource not found | Target ID does not exist in the database. |
| `409 Conflict` | Unique conflict | Duplicate email, category slug, or concurrent update conflict. |
| `422 Unprocessable`| Bean validation error | Missing required fields, invalid email format, negative values. |
| `500 Server Error`| Internal failure | Unhandled exceptions (caught and normalized by GlobalExceptionHandler). |

---

## 2. Standard Envelope Formats

### 2.1 Standard Paginated Envelope
```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 142,
  "totalPages": 8,
  "first": true,
  "last": false,
  "empty": false
}
```

### 2.2 Standard Error Response Envelope
Consistent across all backend exceptions:
```json
{
  "timestamp": "2026-09-05T13:30:00.123Z",
  "status": 400,
  "error": "INVALID_STATE_TRANSITION",
  "message": "Cannot transition incident from RESOLVED directly to ASSIGNED",
  "path": "/api/incidents/14/status",
  "details": [
    {
      "field": "targetStatus",
      "issue": "Allowed transitions from RESOLVED are REOPENED or CLOSED"
    }
  ]
}
```

---

## 3. Detailed Endpoint Catalog

### 3.1 Authentication (`/api/auth`)

#### `POST /api/auth/register`
- **Access**: Public
- **Description**: Registers a new employee user account.
- **Request Body**:
```json
{
  "email": "jane.doe@enterprise.com",
  "password": "SecurePassword123!",
  "firstName": "Jane",
  "lastName": "Doe"
}
```
- **Response `201 Created`**:
```json
{
  "userId": 12,
  "email": "jane.doe@enterprise.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "role": "EMPLOYEE",
  "message": "Registration successful. Please log in."
}
```

#### `POST /api/auth/login`
- **Access**: Public
- **Description**: Authenticates user credentials and returns JWT access and refresh tokens.
- **Request Body**:
```json
{
  "email": "jane.doe@enterprise.com",
  "password": "SecurePassword123!"
}
```
- **Response `200 OK`**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "d8f6e80b-419a-4c28-98e3-05c21975e52d",
  "tokenType": "Bearer",
  "expiresIn": 900000,
  "user": {
    "id": 12,
    "email": "jane.doe@enterprise.com",
    "firstName": "Jane",
    "lastName": "Doe",
    "role": "EMPLOYEE"
  }
}
```

#### `POST /api/auth/refresh`
- **Access**: Public
- **Description**: Rotates refresh token and returns a new access token.
- **Request Body**:
```json
{
  "refreshToken": "d8f6e80b-419a-4c28-98e3-05c21975e52d"
}
```
- **Response `200 OK`**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "b7c2a11e-812e-4b21-99a1-89d123498f01",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

---

### 3.2 User Profile (`/api/users`)

#### `GET /api/users/me`
- **Access**: Authenticated (`EMPLOYEE`, `ENGINEER`, `MANAGER`, `ADMIN`)
- **Description**: Retrieves currently authenticated user profile and team memberships.
- **Response `200 OK`**:
```json
{
  "id": 12,
  "email": "jane.doe@enterprise.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "role": "EMPLOYEE",
  "teamId": 2,
  "teamName": "Operations Squad",
  "createdAt": "2026-09-01T08:00:00Z"
}
```

---

### 3.3 Incidents (`/api/incidents`)

#### `GET /api/incidents`
- **Access**: Authenticated
- **Description**: Lists incidents subject to role-based filtering (Employees see their reported tickets; Engineers see assigned/team tickets; Managers see team tickets; Admins see all).
- **Query Parameters**:
  - `page` (default 0), `size` (default 20), `sort` (default `createdAt,desc`)
  - `status` (`NEW`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, etc.)
  - `priority` (`P1`, `P2`, `P3`, `P4`)
  - `categoryId` (optional filter)
  - `teamId` (optional filter for Managers/Admins)
  - `search` (keyword search in title/number)
- **Response `200 OK`**: Paginated incident summaries (`PageResponse<IncidentSummaryDTO>`).

#### `POST /api/incidents`
- **Access**: Authenticated (`EMPLOYEE`, `ENGINEER`, `MANAGER`, `ADMIN`)
- **Description**: Creates a new incident, computes initial SLA timers, and dispatches creation events.
- **Request Body**:
```json
{
  "title": "VPN Gateway connection failure in East Coast branch",
  "description": "All employees on subnet 10.24.0.0/16 are experiencing timeout when attempting to authenticate with Pulse Secure.",
  "categoryId": 3,
  "priority": "P2",
  "severity": "HIGH"
}
```
- **Response `201 Created`**:
```json
{
  "id": 101,
  "incidentNumber": "INC-20260905-0101",
  "title": "VPN Gateway connection failure in East Coast branch",
  "status": "NEW",
  "priority": "P2",
  "severity": "HIGH",
  "categoryId": 3,
  "categoryName": "Network Infrastructure",
  "reporterId": 12,
  "reporterName": "Jane Doe",
  "assigneeId": null,
  "teamId": null,
  "sla": {
    "responseDueAt": "2026-09-05T14:00:00Z",
    "resolutionDueAt": "2026-09-05T21:30:00Z",
    "isResponseBreached": false,
    "isResolutionBreached": false
  },
  "createdAt": "2026-09-05T13:30:00Z",
  "updatedAt": "2026-09-05T13:30:00Z"
}
```

#### `GET /api/incidents/{id}`
- **Access**: Authenticated (Subject to ownership check)
- **Description**: Returns detailed incident record including SLA record, recent comments, and linked knowledge articles.
- **Response `200 OK`**: Detailed `IncidentDetailDTO`.

#### `PUT /api/incidents/{id}`
- **Access**: `ENGINEER`, `MANAGER`, `ADMIN` (Or reporter before triage)
- **Description**: Updates incident title, description, category, or severity.
- **Response `200 OK`**: Updated `IncidentDetailDTO`.

#### `PATCH /api/incidents/{id}/assign`
- **Access**: `MANAGER`, `ADMIN`
- **Description**: Assigns or reassigns an incident to a specific team and/or engineer.
- **Request Body**:
```json
{
  "teamId": 2,
  "assigneeId": 5,
  "assignmentReason": "Assigned to Network Squad senior engineer for immediate diagnostic."
}
```
- **Response `200 OK`**:
```json
{
  "id": 101,
  "incidentNumber": "INC-20260905-0101",
  "status": "ASSIGNED",
  "teamId": 2,
  "teamName": "Network Squad",
  "assigneeId": 5,
  "assigneeName": "Alex Vance",
  "updatedAt": "2026-09-05T13:45:00Z"
}
```

#### `PATCH /api/incidents/{id}/status`
- **Access**: Role-dependent per state machine rules
- **Description**: Transitions incident status with state machine invariant validation and history logging.
- **Request Body**:
```json
{
  "status": "RESOLVED",
  "comment": "Replaced primary VPN route table in firewall config. Verified connectivity with remote users.",
  "rootCause": "Route collision during morning routing update."
}
```
- **Response `200 OK`**: Updated `IncidentStatusResponseDTO`.

#### `POST /api/incidents/{id}/comments`
- **Access**: Authenticated
- **Description**: Adds a public customer update or internal diagnosis note.
- **Request Body**:
```json
{
  "commentText": "Testing packet flow on interface eth0.",
  "isInternal": true
}
```
- **Response `201 Created`**:
```json
{
  "id": 501,
  "incidentId": 101,
  "authorId": 5,
  "authorName": "Alex Vance",
  "commentText": "Testing packet flow on interface eth0.",
  "isInternal": true,
  "createdAt": "2026-09-05T14:10:00Z"
}
```

#### `GET /api/incidents/{id}/comments`
- **Access**: Authenticated (Subject to incident view permission)
- **Description**: Retrieves all comments for an incident. Internal investigation notes are filtered out for EMPLOYEE callers.
- **Response `200 OK`**: List of `IncidentCommentResponse`.

#### `GET /api/incidents/{id}/history`
- **Access**: Authenticated (Subject to incident view permission)
- **Description**: Retrieves the complete audit history of all status, assignment, and priority transitions for an incident.
- **Response `200 OK`**: List of `IncidentHistoryDTO` sorted by timestamp descending.

---

### 3.4 Teams (`/api/teams`)

#### `GET /api/teams`
- **Access**: Authenticated (`EMPLOYEE`, `ENGINEER`, `MANAGER`, `ADMIN`)
- **Description**: Lists active teams or all teams based on filter, including current member count.
- **Query Parameters**: `activeOnly` (boolean, default false)
- **Response `200 OK`**: `ApiResponse<List<TeamResponse>>`

#### `GET /api/teams/{id}`
- **Access**: Authenticated
- **Description**: Returns detailed team attributes, assigned lead engineer, and roster size.
- **Response `200 OK`**: `ApiResponse<TeamResponse>`
- **Response `404 Not Found`**: When team ID does not exist.

#### `POST /api/teams`
- **Access**: `MANAGER`, `ADMIN`
- **Description**: Creates a new engineering or operations squad with optional lead assignment.
- **Request Body**:
```json
{
  "name": "Database Reliability Squad",
  "description": "L2/L3 support for PostgreSQL, Redis, and data replication pipelines.",
  "leadUserId": 7
}
```
- **Response `201 Created`**: `ApiResponse<TeamResponse>`
- **Response `400 Bad Request`**: Validation failure (missing name, name length, invalid/inactive lead user).
- **Response `403 Forbidden`**: Caller lacks MANAGER/ADMIN role.
- **Response `409 Conflict`**: Team name already exists.

#### `PUT /api/teams/{id}`
- **Access**: `MANAGER`, `ADMIN`
- **Description**: Modifies team attributes (name, description, lead engineer, active status).
- **Request Body**:
```json
{
  "name": "Database Reliability Squad",
  "description": "Updated squad description.",
  "leadUserId": 8,
  "isActive": true
}
```
- **Response `200 OK`**: `ApiResponse<TeamResponse>`
- **Response `403 Forbidden`**: Caller lacks MANAGER/ADMIN role.
- **Response `404 Not Found`**: Team or lead user does not exist.
- **Response `409 Conflict`**: New team name conflicts with an existing team.

#### `GET /api/teams/{id}/members`
- **Access**: Authenticated
- **Description**: Lists all members assigned to the specified team.
- **Response `200 OK`**: `ApiResponse<List<TeamMemberResponse>>`
- **Response `404 Not Found`**: When team does not exist.

#### `POST /api/teams/{id}/members`
- **Access**: `MANAGER`, `ADMIN`
- **Description**: Adds a user to the team roster and synchronizes user primary team.
- **Request Body**:
```json
{
  "userId": 12
}
```
- **Response `201 Created`**: `ApiResponse<TeamMemberResponse>`
- **Response `400 Bad Request`**: Cannot add inactive user.
- **Response `403 Forbidden`**: Caller lacks MANAGER/ADMIN role.
- **Response `404 Not Found`**: Team or user not found.
- **Response `409 Conflict`**: User is already a member of the team.

#### `DELETE /api/teams/{id}/members/{userId}`
- **Access**: `MANAGER`, `ADMIN`
- **Description**: Removes a member from the team roster. Preserves the user entity.
- **Response `200 OK`**: `ApiResponse<Void>`
- **Response `403 Forbidden`**: Caller lacks MANAGER/ADMIN role.
- **Response `404 Not Found`**: Team, user, or membership join not found.

---

### 3.5 Knowledge Base (`/api/knowledge`)

#### `GET /api/knowledge`
- **Access**: Authenticated
- **Description**: Searches and browses published knowledge base articles.
- **Query Parameters**: `categoryId`, `search`, `tag`, `page`, `size`.
- **Response `200 OK`**: Paginated `KnowledgeArticleSummaryDTO`.

#### `POST /api/knowledge`
- **Access**: `ENGINEER`, `MANAGER`, `ADMIN`
- **Description**: Creates a new knowledge base article.
- **Request Body**:
```json
{
  "title": "VPN Gateway Routing Collision Resolution",
  "problem": "Pulse Secure client hangs at 'Connecting (82%)'.",
  "symptoms": "Gateway timeout in logs, ICMP unreachable.",
  "rootCause": "BGP route table desync on primary edge router.",
  "resolution": "Flush edge router BGP peers using 'clear ip bgp * soft' and verify default route.",
  "categoryId": 3,
  "tags": "vpn,network,firewall,pulse-secure",
  "status": "PUBLISHED"
}
```
- **Response `201 Created`**: `KnowledgeArticleDetailDTO`.

#### `GET /api/knowledge/{id}`
- **Access**: Authenticated
- **Description**: Retrieves full article details with problem, symptoms, root cause, and resolution.
- **Response `200 OK`**: `KnowledgeArticleDetailDTO`.

#### `PUT /api/knowledge/{id}`
- **Access**: `ENGINEER`, `MANAGER`, `ADMIN`
- **Description**: Updates article content, taxonomy, or publication status.
- **Response `200 OK`**: Updated `KnowledgeArticleDetailDTO`.

---

### 3.6 Notifications (`/api/notifications`)

#### `GET /api/notifications`
- **Access**: Authenticated
- **Description**: Retrieves current user's notifications (newest first).
- **Query Parameters**: `unreadOnly` (boolean, default false), `page`, `size`.
- **Response `200 OK`**: Paginated `NotificationDTO`.

#### `PATCH /api/notifications/{id}/read`
- **Access**: Authenticated (Owner only)
- **Description**: Marks a specific notification as read.
- **Response `200 OK`**:
```json
{
  "id": 412,
  "isRead": true,
  "readAt": "2026-09-05T14:30:00Z"
}
```

---

### 3.7 Analytics (`/api/analytics`)

#### `GET /api/analytics/overview`
- **Access**: `MANAGER`, `ADMIN`
- **Description**: Real-time operational metrics derived directly from the database.
- **Response `200 OK`**:
```json
{
  "totalIncidents": 1420,
  "openIncidents": 38,
  "criticalIncidents": 3,
  "resolvedIncidents": 1382,
  "slaComplianceRate": 96.4,
  "averageResolutionTimeMinutes": 142.5,
  "incidentsByPriority": {
    "P1": 18,
    "P2": 112,
    "P3": 680,
    "P4": 610
  },
  "incidentsByStatus": {
    "NEW": 5,
    "TRIAGED": 4,
    "ASSIGNED": 11,
    "IN_PROGRESS": 18,
    "RESOLVED": 1382
  },
  "teamWorkload": [
    { "teamId": 2, "teamName": "Network Squad", "activeCount": 12, "criticalCount": 1 },
    { "teamId": 3, "teamName": "Cloud Infra", "activeCount": 9, "criticalCount": 2 }
  ]
}
```

---

### 3.8 AI Advisory (`/api/ai`)

#### `POST /api/ai/analyze-incident`
- **Access**: `ENGINEER`, `MANAGER`, `ADMIN`
- **Description**: Advisory copilot endpoint providing incident summary, category suggestions, severity suggestions, and RAG resolution recommendations.
- **Request Body**:
```json
{
  "incidentId": 101,
  "title": "VPN Gateway connection failure in East Coast branch",
  "description": "All employees on subnet 10.24.0.0/16 are experiencing timeout when attempting to authenticate with Pulse Secure."
}
```
- **Response `200 OK`**:
```json
{
  "isAvailable": true,
  "summary": "East Coast office VPN gateway unreachable due to authentication timeouts on subnet 10.24.0.0/16.",
  "suggestedCategory": { "id": 3, "name": "Network Infrastructure", "confidence": 0.94 },
  "suggestedSeverity": "HIGH",
  "suggestedPriority": "P2",
  "recommendation": "Check edge router BGP peers for route collisions. If logs display ICMP unreachable, clear BGP soft tables per standard SOP.",
  "sources": [
    {
      "sourceType": "KNOWLEDGE_BASE",
      "id": 42,
      "title": "VPN Gateway Routing Collision Resolution",
      "relevanceScore": 0.91
    },
    {
      "sourceType": "HISTORICAL_INCIDENT",
      "id": 88,
      "incidentNumber": "INC-20260812-0044",
      "title": "Pulse Secure Authentication Outage",
      "relevanceScore": 0.86
    }
  ]
}
```

---

### 3.9 Admin (`/api/admin`)

#### `GET /api/admin/users`
- **Access**: `ADMIN`
- **Description**: Paginated list of enterprise users with role and status filters.
- **Response `200 OK`**: `PageResponse<AdminUserDTO>`.

#### `PATCH /api/admin/users/{id}/role`
- **Access**: `ADMIN`
- **Description**: Promotes or changes user role.
- **Request Body**:
```json
{
  "role": "ENGINEER"
}
```
- **Response `200 OK`**: `AdminUserDTO`.

#### `GET /api/admin/audit-logs`
- **Access**: `ADMIN`
- **Description**: Queryable audit log history with actor, resource, action, and date range filters.
- **Response `200 OK`**: `PageResponse<AuditLogDTO>`.

---

### 3.10 Role-Based Access Control (`/api/rbac`)

Endpoints demonstrating explicit method-level authorization (`@PreAuthorize`) and Spring Security authority validation:

#### `GET /api/rbac/employee`
- **Access**: `ROLE_EMPLOYEE`, `ROLE_ADMIN`
- **Description**: Accesses employee-tier operational resources.
- **Response `200 OK`**: `ApiResponse<Map<String, Object>>` with resource info.
- **Response `403 Forbidden`**: Returned for `ENGINEER` or `MANAGER` callers.

#### `GET /api/rbac/engineer`
- **Access**: `ROLE_ENGINEER`, `ROLE_ADMIN`
- **Description**: Accesses engineer-tier diagnostic and technical resources.
- **Response `200 OK`**: `ApiResponse<Map<String, Object>>` with resource info.
- **Response `403 Forbidden`**: Returned for `EMPLOYEE` or `MANAGER` callers.

#### `GET /api/rbac/manager`
- **Access**: `ROLE_MANAGER`, `ROLE_ADMIN`
- **Description**: Accesses manager-tier reporting and workload management resources.
- **Response `200 OK`**: `ApiResponse<Map<String, Object>>` with resource info.
- **Response `403 Forbidden`**: Returned for `EMPLOYEE` or `ENGINEER` callers.

#### `GET /api/rbac/admin`
- **Access**: `ROLE_ADMIN` exclusively
- **Description**: Accesses administrative system configurations.
- **Response `200 OK`**: `ApiResponse<Map<String, Object>>` with resource info.
- **Response `403 Forbidden`**: Returned for `EMPLOYEE`, `ENGINEER`, or `MANAGER` callers.

#### Security & Error Semantics:
- `401 Unauthorized`: Unauthenticated request (missing, invalid, or expired JWT).
- `403 Forbidden`: Authenticated request from a user lacking the requisite role. Emits standard `ApiErrorResponse` envelope.

---

### 3.11 Service Level Agreements (`/api/sla`)

#### `GET /api/sla/policies`
- **Access**: Authenticated (`EMPLOYEE`, `ENGINEER`, `MANAGER`, `ADMIN`)
- **Description**: Retrieves all configured SLA policies, optionally filtering to active policies only.
- **Query Parameters**: `activeOnly` (boolean, default false)
- **Response `200 OK`**: `ApiResponse<List<SlaPolicyResponse>>`

#### `GET /api/sla/policies/{id}`
- **Access**: Authenticated
- **Description**: Retrieves details of a specific SLA policy by ID.
- **Response `200 OK`**: `ApiResponse<SlaPolicyResponse>`
- **Response `404 Not Found`**: Policy ID does not exist.

#### `POST /api/sla/policies`
- **Access**: `MANAGER`, `ADMIN`
- **Description**: Creates a new SLA policy with configurable response, resolution, and escalation thresholds.
- **Request Body**:
```json
{
  "name": "Custom Urgent SLA",
  "priority": "P1",
  "responseTimeMinutes": 10,
  "resolutionTimeMinutes": 60,
  "escalationThresholdMinutes": 30,
  "isActive": true
}
```
- **Response `201 Created`**: `ApiResponse<SlaPolicyResponse>`
- **Response `400 Bad Request`**: Validation failure (resolution target < response target, negative durations, invalid priority).
- **Response `403 Forbidden`**: Caller lacks MANAGER/ADMIN role.
- **Response `409 Conflict`**: Policy for priority tier already exists.

#### `PUT /api/sla/policies/{id}`
- **Access**: `MANAGER`, `ADMIN`
- **Description**: Updates targets and active status of an existing SLA policy.
- **Request Body**:
```json
{
  "name": "Updated Critical SLA",
  "responseTimeMinutes": 20,
  "resolutionTimeMinutes": 120,
  "escalationThresholdMinutes": 60,
  "isActive": true
}
```
- **Response `200 OK`**: `ApiResponse<SlaPolicyResponse>`
- **Response `400 Bad Request`**: Validation failure (resolution target < response target).
- **Response `403 Forbidden`**: Caller lacks MANAGER/ADMIN role.
- **Response `404 Not Found`**: Policy ID not found.

#### `GET /api/sla/records/incident/{incidentId}`
- **Access**: Authenticated (Subject to incident resource-level access control)
- **Description**: Retrieves the SLA record, deadlines, compliance status, and breach flags for a specific incident.
- **Response `200 OK`**: `ApiResponse<SlaRecordResponse>`
```json
{
  "status": 200,
  "message": "Operation completed successfully",
  "data": {
    "id": 101,
    "incidentId": 14,
    "incidentNumber": "INC-20260905-0101",
    "slaPolicyId": 1,
    "slaPolicyName": "Critical Priority SLA (P1)",
    "priority": "P1",
    "responseDueAt": "2026-09-05T13:45:00Z",
    "resolutionDueAt": "2026-09-05T15:30:00Z",
    "respondedAt": "2026-09-05T13:40:00Z",
    "resolvedAt": null,
    "isResponseBreached": false,
    "isResolutionBreached": false,
    "isBreached": false,
    "createdAt": "2026-09-05T13:30:00Z"
  }
}
```
- **Response `403 Forbidden`**: Caller lacks permission to view the incident.
- **Response `404 Not Found`**: Incident or SLA record not found.


