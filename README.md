# Fairness-Checker

Spring Boot gateway for the on-call fairness tracker: ingests PagerDuty incidents and on-call shifts, stores
them in PostgreSQL, and serves scores computed by the
[fairneess-scorer](https://github.com/biradarsrikanth/fairneess-scorer) service.
Design: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Run with Docker

```bash
cp .env.example .env              # database, PagerDuty and SCORER_API_KEY (same key as the scorer)
docker network create fairness-net   # once per machine; shared with fairneess-scorer
docker compose up --build
```

- The compose file doesn't run a database; it connects to the one in `AZURE_DB_*`. On startup, Flyway applies
  any pending migrations from `src/main/resources/db/migration` to that database.
- Start the scorer from its own repo on the same network and scores work at
  `http://localhost:8080/api/v1/scores/fairness`; without it, score endpoints return 503 and the rest works.

## Run without Docker

Needs JDK 21.

```bash
./mvnw spring-boot:run            # reads .env; the scorer is expected at PYTHON_SCORER_URL
./mvnw verify                     # tests; SchemaContractTest needs Docker for PostgreSQL
```

## API

| Area | Routes |
|---|---|
| Teams | `GET/POST /api/teams`, `GET/PUT/DELETE /api/teams/{id}` |
| Engineers | `GET/POST /api/engineers`, `GET/PUT/DELETE /api/engineers/{id}` (deactivate instead of deleting engineers with history) |
| Alerts | `GET/POST /api/alerts`, `GET /api/alerts/{id}`, `GET /api/alerts/{id}/assignments`, `GET /api/alerts/filter` |
| On-call | `GET /api/oncall?from=&to=&engineerId=` |
| Scores | `GET /api/v1/scores/{fairness,burnout,timeofday}`, `GET /api/v1/scores/engineers/{id}` (`days` or `start`/`end`, optional `team`) |
| PagerDuty | `POST /api/pagerduty/sync`, `POST /api/pagerduty/sync/oncalls?days=`, `POST /api/webhook/pagerduty` |
| Operations | `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus` |
