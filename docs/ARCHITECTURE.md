# Architecture

On-call fairness and burnout tracker for PagerDuty. Three repositories: a web app, and two services that
share one PostgreSQL database with a strict ownership split.

```mermaid
flowchart LR
    Browser -- "HTTPS" --> WEB["fairness-web<br/>Angular app served by Nginx"]
    WEB -- "/api (same origin)" --> GW
    PD[PagerDuty] -- "webhook (HMAC-SHA256)" --> GW
    GW -- "incremental sync (REST)" --> PD
    GW["Fairness-Checker<br/>Spring Boot · gateway + system of record"]
    SC["fairneess-scorer<br/>FastAPI · stateless scoring"]
    GW -- "GET /v1/scores/*<br/>X-API-Key · X-Request-ID<br/>timeouts + retry" --> SC
    GW -- "JPA read/write<br/>Flyway owns the schema" --> DB[(PostgreSQL)]
    SC -- "read-only<br/>scoring_*_v views" --> DB
```

## Responsibilities

| Concern | fairness-web (Angular) | Fairness-Checker (Java) | fairneess-scorer (Python) |
|---|---|---|---|
| Role | The user interface | Public gateway, ingestion, auth, analytics, reports, system of record | Internal, stateless computation |
| Database | None | Owns every table; the only writer | Reads the four `scoring_*_v` views only |
| Schema changes | None | Flyway migrations in `src/main/resources/db/migration`; Hibernate only validates | None |
| Exposure | Public; Nginx passes `/api` to the gateway, so the browser sees one origin | Public behind the web app (or directly, for the API) | Private network only, API key required |

## Code layout (gateway)

Packages are organised by feature under `com.example.fairnesstracker`; each holds its controller, service,
repository, entity, `dto/` records and a MapStruct mapper (entity → response; requests are turned into
entities by the services, which own the rules). Controllers only call services:

| Package | Contents |
|---|---|
| `team`, `engineer`, `alert`, `oncall` | Core data and its REST API (`alert` also holds `MonitoredService`) |
| `auth`, `user`, `security` | Sign-in, refresh tokens, lockout and rate limits; user accounts and the first admin; JWT filter chain, `AccessPolicy` and `DataScope` (what the caller may see) |
| `analytics` | The analytics page: counts and averages in SQL over `analytics_alert_v`, Gini, burnout and trends from the scorer, all in one `Snapshot` |
| `report` | PDF and Excel reports built from the same `Snapshot` (`render/`), and the `report_export` history |
| `audit` | `audit_log` entries (sign-ins, user changes, syncs, replays, exports) and the admin listing |
| `pagerduty` | PagerDuty client calls, incident and on-call sync, scheduler, sync activity for admins; `sync/` runs syncs under a lock and records them; `webhook/` receives signed webhooks and stores each event once |
| `scoring` | `ScoringClient` and the `/api/v1/scores` proxy to the scorer |
| `common` | `config/` (HTTP clients, OpenAPI), `exception/` (`ApiError`, global handler), `web/` (request id filter) |

All routes are under `/api/v1`, except `/api/webhook/pagerduty`, whose URL is configured in PagerDuty.
The OpenAPI spec is served at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

## Data model

```mermaid
erDiagram
    team ||--o{ engineer_data : "has"
    team |o--o{ service : "owns"
    engineer_data ||--o{ alert_event : "was paged for (primary)"
    service |o--o{ alert_event : "raised"
    alert_event ||--|{ alert_assignment : "paging history"
    engineer_data |o--o{ alert_assignment : "received"
    engineer_data ||--o{ oncall_shift : "was on call"

    team { bigint id PK
           varchar name UK
           varchar timezone "IANA, drives night/weekend" }
    engineer_data { bigint id PK
                    bigint team_id FK
                    varchar pager_duty_user_id UK
                    varchar email UK "case-insensitive"
                    boolean active }
    service { bigint id PK
              varchar pagerduty_service_id UK
              bigint team_id FK "nullable" }
    alert_event { bigint id PK
                  varchar pager_duty_incident_id UK
                  bigint engineer_id FK "who was paged"
                  bigint service_id FK
                  varchar severity "P1..P5"
                  varchar status "triggered|acknowledged|resolved"
                  timestamp triggered_at
                  timestamp acknowledged_at
                  timestamp resolved_at }
    alert_assignment { bigint id PK
                       bigint alert_id FK
                       bigint engineer_id FK
                       varchar kind "PAGED|ESCALATED|REASSIGNED"
                       timestamp assigned_at
                       varchar source "LOG|WEBHOOK|BACKFILL" }
    oncall_shift { bigint id PK
                   bigint engineer_id FK
                   smallint escalation_level
                   timestamp starts_at
                   timestamp ends_at }
```

Accounts and operations (V4, V5):

```mermaid
erDiagram
    app_user |o--o| engineer_data : "is (engineers)"
    app_user ||--o{ user_team_access : "manages (managers)"
    team ||--o{ user_team_access : ""
    app_user ||--o{ refresh_token : "signed in"
    app_user |o--o{ report_export : "downloaded"
    app_user |o--o{ sync_run : "triggered"

    app_user { bigint id PK
               varchar email UK "case-insensitive"
               varchar password_hash "BCrypt"
               varchar role "ADMIN|MANAGER|ENGINEER"
               int failed_login_count
               timestamptz locked_until
               boolean must_change_password }
    refresh_token { bigint id PK
                    varchar token_hash "SHA-256"
                    uuid family_id "one sign-in"
                    timestamptz expires_at
                    timestamptz revoked_at
                    bigint replaced_by_id FK }
    report_export { bigint id PK
                    varchar report_type "ANALYTICS|ENGINEER"
                    varchar format "PDF|XLSX"
                    date window_start
                    date window_end
                    varchar status "RUNNING|SUCCEEDED|FAILED" }
    sync_run { bigint id PK
               varchar kind "INCIDENTS|ONCALLS"
               varchar trigger_type "SCHEDULED|MANUAL"
               varchar status }
    audit_log { bigint id PK
                varchar action
                bigint user_id "kept after the user goes"
                jsonb details
                varchar request_id }
    webhook_event { bigint id PK
                    varchar pagerduty_event_id UK
                    varchar status "RECEIVED|PROCESSED|IGNORED|FAILED"
                    jsonb payload }
```

`shedlock` (V5) holds the sync locks. `audit_log` has no foreign keys on purpose: entries outlive the users
and records they mention.

Conventions:
- **Event times** (`triggered_at`, `starts_at`, …) are `timestamp` in **UTC**, as PagerDuty reports them. The
  API returns them as UTC instants (`"2026-09-27T18:00:00Z"`), so clients never read them as local time.
  **Audit columns** `created_at`/`updated_at` are `timestamptz`, filled by the database (default + trigger), so
  every writer gets them without application code.
- **Integrity lives in the database:** foreign keys, `CHECK` constraints for enums and time ordering, unique
  keys that make every sync idempotent. The application validates too, but the database is the last line.
- **`alert_event.engineer_id`** is the primary (first-paged) engineer, kept on the row for fast scoring;
  `alert_assignment` holds the full history, rebuilt from the PagerDuty incident log when an incident
  changes. The incident log is the source of truth; webhook rows are provisional until the next sync.
- **Engineers are deactivated, not deleted,** once they have history.

## Contracts

**Database contract: the `scoring_*_v` views** (`scoring_engineer_v`, `scoring_alert_v`,
`scoring_assignment_v`, `scoring_oncall_v`; created in V2 and extended in V3). The scorer never reads tables
directly. Tables can be refactored freely as long as the views keep their columns; views may gain columns
(appended, via `CREATE OR REPLACE VIEW`), but renaming or removing one is a breaking change and needs a
coordinated release. View timestamps are UTC unless the column name ends in `_local` (the engineer's team
timezone). The gateway's own analytics read `analytics_alert_v` (V6) the same way: one row per alert with its
team-local time and the scorer's after-hours definition, so the page's counts and the scorer's figures agree.

**HTTP contract: scorer `/v1/scores/*`.** Versioned in the path. Pydantic models on the scorer side and Java
records in `scoring/dto` describe the same JSON; a breaking change means a new `/v2` path, not an edit to `/v1`.
Every call carries:
- `X-API-Key`, a shared secret (`SCORER_API_KEY`), compared in constant time.
- `X-Request-ID`, generated by the gateway per incoming request and written to both services' logs, so one
  user request can be traced across services.

**HTTP contract: the gateway's lists.** Unbounded lists (alerts, engineers, on-call shifts) are paged:
`page` (from 0), `size` (default 20, capped at 100) and `sort=field,asc|desc`, answered with `PageResponse`
(`content`, `page`, `size`, `totalElements`, `totalPages`), our own type rather than Spring's `Page`, so the JSON
doesn't depend on Spring Data. Each endpoint names the fields it can sort by (anything else is a
`VALIDATION_FAILED` on `sort`) and loads a page with at most two queries (`@EntityGraph` on the list query;
`PaginationTest` counts them). Filters are optional query parameters that combine with AND; date ranges are
`start`/`end`, both inclusive, as days in `TEAM_TIMEZONE` (the audit log's are UTC days). Small, bounded lists (teams, an alert's paging history) are plain arrays.

**HTTP contract: the gateway's errors.** Every error, from any layer, is an `ApiError`: RFC 9457 problem
details served as `application/problem+json`.

```json
{"type": "about:blank", "title": "Bad Request", "status": 400,
 "detail": "email: must be a well-formed email address; name: must not be blank",
 "instance": "/api/v1/engineers", "code": "VALIDATION_FAILED",
 "errors": [{"field": "email", "message": "must be a well-formed email address"},
            {"field": "name", "message": "must not be blank"}],
 "requestId": "6f482981b48f4a09a8e64a0ddf0a866f", "timestamp": "2026-10-12T16:39:45.614Z"}
```

- Clients branch on `code` (`ErrorCode`), never on `detail`, which is for people and may change.
- `errors` names the offending body fields or query/path parameters, so a form can show each message
  next to its input; it is empty when the problem isn't about one field.
- `requestId` equals the `X-Request-ID` response header and appears in the logs of both services.
- Server errors (`INTERNAL_ERROR`) never include exception messages, hosts or SQL.

| Code | Status | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Bean validation, unparseable values, unknown references (e.g. a team name) |
| `MALFORMED_REQUEST` | 400 | Missing body or broken JSON |
| `BAD_REQUEST` | 400 (or other 4xx) | Any other client error |
| `UNAUTHENTICATED` / `INVALID_CREDENTIALS` | 401 | No or expired access token; wrong email or password |
| `REFRESH_TOKEN_INVALID` / `REFRESH_TOKEN_REUSED` | 401 | The refresh cookie is missing, expired or revoked; a rotated one came back (the sign-in is ended) |
| `FORBIDDEN` / `PASSWORD_CHANGE_REQUIRED` / `ACCOUNT_DISABLED` | 403 | Outside the caller's role or scope; the password must be changed first; the account is off |
| `ACCOUNT_LOCKED` / `TOO_MANY_REQUESTS` | 423 / 429 | Too many wrong passwords; too many sign-ins from one IP (both with `Retry-After`) |
| `NOT_FOUND` | 404 | The resource in the URL, or the route, doesn't exist |
| `METHOD_NOT_ALLOWED` / `NOT_ACCEPTABLE` / `UNSUPPORTED_MEDIA_TYPE` | 405 / 406 / 415 | Protocol errors |
| `CONFLICT` | 409 | Duplicates (with the field) and operations the current state forbids |
| `SYNC_IN_PROGRESS` | 409 | A manual PagerDuty sync while the same kind of sync is running |
| `UPSTREAM_ERROR` / `UPSTREAM_UNAVAILABLE` / `UPSTREAM_TIMEOUT` | 502 / 503 / 504 | Scorer or PagerDuty failed |
| `INTERNAL_ERROR` | 500 | A bug; quote the `requestId` |

Exceptions map onto this in `common/exception`: services throw `ValidationException`,
`ConflictException` or `ResourceNotFoundException` (all `ApiException`s carrying an `ErrorCode`);
`GlobalExceptionHandler` converts those and Spring's own exceptions; `ApiErrorController` covers errors that
never reach a controller.

## Data flow

1. **Ingestion.** PagerDuty pushes webhooks (verified with HMAC-SHA256). Each signed event is stored in
   `webhook_event` under PagerDuty's event id before it's applied, so a redelivery is a no-op (an old
   `incident.acknowledged` delivered again can't reopen a resolved incident). Events applied out of order
   can't move an incident back either: one older than the incident's last change only fills in what's missing,
   and only `incident.reopened` clears a resolution. PagerDuty is never called inside a database transaction.
   An event that fails is kept as
   FAILED and answered with an error; PagerDuty's retry applies it again, and admins can replay it. A
   scheduled sync pulls incidents created in the last `PAGERDUTY_SYNC_LOOKBACK_DAYS` days as a safety net
   for missed webhooks, upserting by PagerDuty incident id (unique index).
2. **On-call shifts.** The same scheduled job reads PagerDuty `/oncalls` (in ≤ 90-day chunks) into
   `oncall_shift`, merging overlapping pieces of one shift.
   Every sync, scheduled or manual, holds a ShedLock lock for its kind (incidents, on-calls) in the
   `shedlock` table, so across instances one of each runs at a time; a manual sync that finds the lock taken
   gets 409 `SYNC_IN_PROGRESS`. Each sync that runs is recorded in `sync_run` (RUNNING, then SUCCEEDED or
   FAILED with the error). A run left RUNNING by an instance that died is closed as FAILED by the next run of
   that kind.
3. **Attribution.** An alert belongs to the engineer who was **paged**: the first assignee, falling back to
   the incident's first assignment log entry. It is set once on creation and never overwritten by later
   updates, so resolving someone else's incident doesn't move the alert to you.
   The incident log (read when an incident is new or changes status) rebuilds the alert's paging history
   (`alert_assignment`: paged, escalated, reassigned) and sets `acknowledged_at`.
4. **Scoring.** The gateway forwards score requests to the scorer, which loads the window from the views
   (cached for 60 s), computes scores in pandas and returns typed JSON. Scoring rules are in
   `fairneess-scorer/docs/SCORING.md`.
5. **Analytics.** `GET /api/v1/analytics/snapshot` answers the whole analytics page in one call: KPIs with the
   previous period for comparison, alerts over time, the hour-by-weekday heatmap and the noisiest services come
   from SQL over `analytics_alert_v`; Gini, burnout and the trend come from the scorer. No database connection is
   held while the scorer is called, and when it can't answer, the SQL figures are still returned with a status
   saying why the rest is missing.
6. **Reports.** `GET /api/v1/reports/...?format=pdf|xlsx` builds the same snapshot and renders it while the
   request waits: Excel with POI (streaming), PDF from a Thymeleaf template with JFreeChart charts through
   openhtmltopdf. Each download is a `report_export` row and a `REPORT_EXPORTED` audit entry.

## Reliability

| Failure | Behaviour |
|---|---|
| Scorer slow | Connect timeout 2 s, response timeout 10 s → gateway returns 504 |
| Scorer down / 5xx | GET retried twice with exponential backoff (200 ms base) → then 503 |
| Scorer 4xx | Passed through (e.g. 404 unknown engineer), never retried |
| PagerDuty slow / down | Same timeouts on the PagerDuty client; a failed sync is recorded as a FAILED `sync_run` and retried next cycle; a webhook that needed PagerDuty answers 502 and is applied on PagerDuty's retry |
| Gateway dies mid-sync | Its lock expires after `PAGERDUTY_SYNC_LOCK_AT_MOST_FOR`; the next run closes the abandoned `sync_run` |
| Database down | Scorer readiness (`/health`) and gateway readiness (`/actuator/health/readiness`) fail; compose / the platform stops routing traffic |

## Security

- Secrets only come from the environment (`.env` locally, App Service settings in Azure); `.env` files are
  git-ignored and excluded from Docker images.
- Scorer: API key on all `/v1` routes; health and metrics are unauthenticated and must stay on the private
  network. Error responses never include database details.
- Database: the scorer should log in as a read-only role limited to the four `scoring_*_v` views
  (`ops/sql/create_scorer_readonly_role.sql`).
- Webhooks: HMAC-SHA256 with constant-time comparison; while a secret is rotated PagerDuty sends one
  signature per secret, and any match is accepted.
- Gateway: every route needs an access token except `/`, `/error`, health/info/prometheus, the PagerDuty
  webhook, `POST /api/v1/auth/{login,refresh,logout}` and the API docs. Security errors are `ApiError`s
  like any other (401 `UNAUTHENTICATED`, 403 `FORBIDDEN`, ...).
- Tokens: the gateway issues its own. Access token: HS256 JWT (`sub` = user id, `email`, `role`), 15 minutes,
  kept in memory by the client. Refresh token: opaque, single-use, rotated on every refresh, stored as a
  SHA-256 hash, sent as an httpOnly `SameSite=Strict` cookie limited to `/api/v1/auth`; reusing a rotated
  one ends that sign-in everywhere. No sessions, so no CSRF tokens. Each request checks that the account still
  exists and is enabled (the same lookup as the caller's scope), so disabling or deleting a user takes effect
  at once (401 `UNAUTHENTICATED`). A changed password refuses other sign-ins' refresh at once; their access
  tokens run out within 15 minutes.
- Sign-in: BCrypt (cost 12); 5 wrong passwords lock the account for 15 minutes; 10 attempts per minute per
  client IP (in memory, per instance). The client IP comes from Nginx, which ignores a client's own
  `X-Forwarded-For` and believes one only from `TRUSTED_PROXY`. There's no sign-up; the first admin comes from `ADMIN_BOOTSTRAP_*`
  and must change the password before anything else.
- Access (`AccessPolicy`): roles are checked with `@PreAuthorize`, data with the user's scope, read from the
  database on each request so changes apply at once:

  | | ADMIN | MANAGER | ENGINEER |
  |---|---|---|---|
  | Alerts, engineers, on-call shifts | all | their teams' | their own |
  | Fairness and time-of-day scores | all teams | their teams | own team, only their own row |
  | Burnout list; one engineer's scores | all | their teams | no; only their own |
  | Change engineers, alerts; edit a team | yes | in their teams | no |
  | Analytics page and its reports | all teams | their teams | their own alerts; their team's fairness with only their own row |
  | One engineer's page and report | anyone | their teams' engineers | only their own |
  | Report history (`/reports/exports`) | their own | their own | their own |
  | Create/delete teams, delete alerts, PagerDuty operations, users, audit log | yes | no | no |

  Lists are filtered rather than refused; a single record outside the scope is a 403.
- Web app: the browser only talks to its own origin (Nginx passes `/api` on), so the refresh cookie can be
  `SameSite=Strict` and there's no CORS. Nginx sends a Content-Security-Policy that allows scripts from that
  origin only (the production build has no inline script), `frame-ancestors 'none'`, HSTS when the request came
  over HTTPS (`X-Forwarded-Proto` from the TLS proxy in front), `nosniff`, `Referrer-Policy` and
  `Permissions-Policy`. Styles allow `'unsafe-inline'`, which Angular's component styles need. The access token
  lives in memory only, never in storage.

## Observability

| | Gateway | Scorer |
|---|---|---|
| Logs | Structured JSON (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`) with `requestId` | JSON (`LOG_FORMAT=json`) with `request_id`, method, path, status, duration |
| Metrics | `/actuator/prometheus` | `/metrics` |
| Health | `/actuator/health/liveness`, `/actuator/health/readiness` | `/health/live`, `/health` |

## Delivery

- **CI:** the gateway and the scorer each run their own workflow on pull requests and pushes to `main`. Java:
  `mvn verify`, including a Testcontainers test that applies every Flyway migration to a real PostgreSQL and
  validates the JPA mapping, then an image build. Python: ruff, mypy, unit tests and integration tests against
  a PostgreSQL service container, then an image build. The web app has no workflow; locally it runs lint,
  Vitest unit tests, the production build and a Playwright end-to-end test against a running stack.
- **CD:** switched off for now, so merges don't deploy anything. To bring back the Azure deploy, restore
  `.github/workflows/main_fairness-tracker.yml` from git history (commit `7a7316f^`); Flyway then runs pending
  migrations when the deployed app starts.
- **Local (Docker):** each repo has its own `docker-compose.yml` and runs independently. They share the external
  Docker network `fairness-net` (`docker network create fairness-net`, once per machine), where the gateway
  reaches the scorer at `http://scorer:8000` and the web app's Nginx reaches the gateway at
  `http://gateway:8080`. None of them runs a database: the gateway and the scorer connect to the existing
  PostgreSQL from their `.env`. The scorer port is bound to `127.0.0.1` only; the web app is on `:4280`.
- **Operations:** [RUNBOOK.md](RUNBOOK.md) covers starting, checking, migrating, rotating secrets and the usual
  failures.

## Configuration

New settings introduced with this architecture (existing database and PagerDuty settings are unchanged):

| Variable | Service | Required | Default | Purpose |
|---|---|---|---|---|
| `SCORER_API_KEY` | both | **yes** (≥ 32 chars, same value on both) | none | Gateway → scorer authentication |
| `JWT_SECRET` | gateway | **yes** (≥ 32 chars) | none | Signs access tokens (HS256); changing it signs everyone out |
| `JWT_ISSUER` / `JWT_AUDIENCE` / `JWT_ACCESS_TOKEN_TTL` | gateway | no | `fairness-checker` / `fairness-web` / `15m` | Access token claims and lifetime (1 minute to 1 hour) |
| `REFRESH_TOKEN_TTL` / `REFRESH_TOKEN_REUSE_GRACE` | gateway | no | `7d` / `10s` | Session length without use; how soon after a refresh the old token may be presented again (two tabs) before it counts as theft and ends the session |
| `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` / `ADMIN_BOOTSTRAP_NAME` | gateway | both or neither (password ≥ 12 chars) | none / none / `Administrator` | First admin, created while there are no users; must change the password at first sign-in |
| `LOGIN_MAX_FAILED_ATTEMPTS` / `LOGIN_LOCK_DURATION` / `LOGIN_ATTEMPTS_PER_IP_PER_MINUTE` | gateway | no | `5` / `15m` / `10` | Account lockout and per-IP sign-in limit |
| `REFRESH_COOKIE_SECURE` | gateway | no | `true` | Set `false` only for plain HTTP on a host other than localhost |
| `FORWARD_HEADERS_STRATEGY` | gateway | no | `native` | Trust `X-Forwarded-*` from private-network proxies (client IP for rate limits) |
| `PYTHON_SCORER_URL` | gateway | in Azure | `http://127.0.0.1:8000` | Where the scorer runs |
| `SCORER_CONNECT_TIMEOUT` / `SCORER_RESPONSE_TIMEOUT` / `SCORER_MAX_RETRIES` | gateway | no | `2s` / `10s` / `2` | Scorer call resilience |
| `PAGERDUTY_CONNECT_TIMEOUT` / `PAGERDUTY_RESPONSE_TIMEOUT` | gateway | no | `5s` / `30s` | PagerDuty call timeouts |
| `PAGERDUTY_SYNC_ENABLED` / `PAGERDUTY_SYNC_LOOKBACK_DAYS` | gateway | no | `true` / `30` | Scheduled incident sync |
| `PAGERDUTY_ONCALL_LOOKBACK_DAYS` | gateway | no | `7` | Scheduled on-call sync; backfill with `POST /api/v1/pagerduty/sync/oncalls?days=365` |
| `PAGERDUTY_SYNC_LOCK_AT_MOST_FOR` | gateway | no | `30m` | How long a sync lock outlives an instance that died holding it; keep it above the longest sync |
| `SEED_DEMO_DATA` | gateway | no | `false` | Demo engineers in an empty database |
| `JPA_SHOW_SQL` | gateway | no | `false` | Log SQL (debugging only) |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | gateway | no | plain text | `ecs` for JSON logs |
| `API_DOCS_ENABLED` | gateway | no | `true` | Serve the OpenAPI spec (`/v3/api-docs`) and Swagger UI (`/swagger-ui.html`) |
| `TEAM_TIMEZONE` | gateway | no | `Asia/Kolkata` | How analytics dates are read; set it to the scorer's value so both cover the same hours |
| `TEAM_TIMEZONE` / `CACHE_TTL_SECONDS` | scorer | no | `Asia/Kolkata` / `60` | Timezone for `start`/`end` dates (night/weekend use each team's own timezone); result cache |
| `REPORT_MAX_WINDOW_DAYS` / `REPORT_MAX_ALERT_ROWS` / `REPORT_MAX_SHIFT_ROWS` | gateway | no | `366` / `5000` / `2000` | Longest report window (at most 366, the on-call listing's limit); rows listed in the workbook |
| `REPORT_MAX_CONCURRENT` | gateway | no | `2` | Reports built at once per instance; more get 429 with `Retry-After` |
| `LOG_FORMAT` / `LOG_LEVEL` | scorer | no | `text` (`json` in Docker) / `INFO` | Logging |
| `GATEWAY_URL` | web (Nginx) | no | `http://gateway:8080` | Where Nginx passes `/api`, `/v3/api-docs` and `/swagger-ui` |
| `TRUSTED_PROXY` | web (Nginx) | no | `127.0.0.1/32` (none) | Address or CIDR of the load balancer in front, whose `X-Forwarded-For` names the client |
| `E2E_BASE_URL` / `E2E_EMAIL` / `E2E_PASSWORD` | web (Playwright) | no | `http://localhost:8088` and a manager | What the end-to-end test signs in to |

## Known limitations

- Several gateway instances can run (syncs are locked across them), but the sign-in rate limit is counted
  per instance, and each instance schedules the sync from its own start time (so with N instances, up to N
  syncs per `PAGERDUTY_SYNC_RATE`, never two of a kind at once).
- `webhook_event` and `sync_run` keep every row; nothing prunes them yet.
- The scorer loads the full window into memory; fine up to ~10⁵ alerts per window. Beyond that, move
  aggregation into SQL.
- Reports are built while the request waits (Nginx allows 120 s) and held in memory; the row limits keep them
  small, and at most `REPORT_MAX_CONCURRENT` are built at once. A queue would be the next step if reports grew.
- PDF text is set in DejaVu Sans: scripts that need shaping (Devanagari, for one) don't print.
