# Fairness-Checker

Spring Boot gateway for the on-call fairness tracker: ingests PagerDuty incidents and on-call shifts, stores
them in PostgreSQL, and serves scores computed by the
[fairneess-scorer](https://github.com/biradarsrikanth/fairneess-scorer) service.
Design: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Run with Docker

```bash
cp .env.example .env              # database, PagerDuty, JWT_SECRET and SCORER_API_KEY (same key as the scorer)
docker network create fairness-net   # once per machine; shared with fairneess-scorer
docker compose up --build
```

- The compose file doesn't run a database; it connects to the one in `AZURE_DB_*`. On startup, Flyway applies
  any pending migrations from `src/main/resources/db/migration` to that database. For a shared database, apply
  them first with `ops/migrate-db.sh`: it backs the database up, rehearses the migrations on a restored copy,
  and only then migrates the real one.
- Start the scorer from its own repo on the same network and scores work at
  `http://localhost:8080/api/v1/scores/fairness`; without it, score endpoints return 503 and the rest works.

## Run without Docker

Needs JDK 21.

```bash
./mvnw spring-boot:run            # reads .env; the scorer is expected at PYTHON_SCORER_URL
./mvnw verify                     # tests; SchemaContractTest needs Docker for PostgreSQL
```

## API

Interactive docs: <http://localhost:8080/swagger-ui.html> (OpenAPI spec at `/v3/api-docs`; `API_DOCS_ENABLED=false` hides both).

**Signing in.** Every route except sign-in, the webhook and health needs an access token:
`POST /api/v1/auth/login` with `{"email", "password"}` returns `accessToken` (send it as
`Authorization: Bearer ...`, valid 15 minutes) and sets an httpOnly refresh cookie; `POST /api/v1/auth/refresh`
gets a new access token. In Swagger UI, sign in with the login operation, then paste the token under
**Authorize**. `Requests.http` does this for the VS Code REST Client. The first admin comes from
`ADMIN_BOOTSTRAP_EMAIL`/`ADMIN_BOOTSTRAP_PASSWORD` and must change that password before anything else; admins
create everyone else. What each role may see is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#security).

Paged lists take `page` (from 0), `size` (default 20, at most 100) and `sort=field,asc|desc`, and return
`{content, page, size, totalElements, totalPages}`. Dates (`start`, `end`) are UTC days, both inclusive; times
in responses are UTC instants. Errors are `application/problem+json` with a stable `code` (see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#contracts)).

| Area | Routes |
|---|---|
| Teams | `GET/POST /api/v1/teams`, `GET/PUT/DELETE /api/v1/teams/{id}` |
| Engineers | `GET /api/v1/engineers?team=&active=` (paged), `POST /api/v1/engineers`, `GET/PUT/DELETE /api/v1/engineers/{id}` (deactivate instead of deleting engineers with history) |
| Alerts | `GET /api/v1/alerts?engineerId=&team=&severity=&status=&start=&end=` (paged), `POST /api/v1/alerts`, `GET/DELETE /api/v1/alerts/{id}`, `GET /api/v1/alerts/{id}/assignments` |
| On-call | `GET /api/v1/oncall?start=&end=&engineerId=` (paged) |
| Scores | `GET /api/v1/scores/{fairness,burnout,timeofday}`, `GET /api/v1/scores/engineers/{id}` (`days` or `start`/`end`, optional `team`) |
| PagerDuty | `POST /api/v1/pagerduty/sync`, `POST /api/v1/pagerduty/sync/oncalls?days=`, `POST /api/webhook/pagerduty` (unversioned: PagerDuty is configured with this URL) |
| Auth | `POST /api/v1/auth/{login,refresh,logout,change-password}`, `GET /api/v1/auth/me` |
| Users (admins) | `GET/POST /api/v1/users`, `GET/PUT/DELETE /api/v1/users/{id}`, `PUT /api/v1/users/{id}/teams`, `POST /api/v1/users/{id}/{reset-password,unlock}` |
| Audit (admins) | `GET /api/v1/audit?action=&userId=&start=&end=` (paged) |
| Operations | `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus` |
