# Runbook

Day-to-day operation of the fairness tracker. How it's built: [ARCHITECTURE.md](ARCHITECTURE.md).

## What runs where

| Part | Port | Healthy when | Logs |
|---|---|---|---|
| fairness-web (Nginx) | 80 in the container (`:4280` with its compose file, `:8088` in the demo) | `GET /healthz` answers `ok` | Nginx access log |
| Fairness-Checker (gateway) | 8080 | `/actuator/health/readiness` is `UP` (includes the database) | JSON with `requestId` (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`) |
| fairneess-scorer | 8000, private | `/health` is `ok` (includes the database); `/health/live` for liveness | JSON with `request_id` |
| PostgreSQL | 5432 | `pg_isready` | |

Every API response carries `X-Request-ID`, and error bodies repeat it as `requestId`. The same id is in the
gateway's and the scorer's log lines for that request: search both for it when someone reports an error.

## Start and stop

**Demo** (everything, a throwaway database with made-up data; from the folder that holds the three repos):

```bash
docker compose up --build -d      # http://localhost:8088, sign-ins in the file's header
docker compose ps                 # seed shows "Exited (0)" once the data is in
docker compose down               # stop, keep the data
docker compose down -v            # stop and forget the data; the next start seeds afresh
```

**Against the real database**, each repo from its own folder, after `docker network create fairness-net` once
per machine: start the scorer, then the gateway, then the web app (`docker compose up --build -d` in each).
Each needs its own `.env` (copy `.env.example`). The gateway applies pending migrations when it starts, so
migrate a shared database first (below).

## Release order

1. Back up and migrate the database with `ops/migrate-db.sh` if the release has new migrations.
2. Gateway. At startup it applies any pending migrations (none, after step 1), then validates the schema and
   refuses to start against one it doesn't match.
3. Scorer, if it changed. Views only ever gain columns, so an older scorer keeps working on a newer schema.
4. Web app. `index.html` is never cached, so browsers pick up the new build on their next page load.

## Database

**Migrating** (from the gateway repo, after `sh mvnw -B package`): `ops/migrate-db.sh`. It backs the database up
with `pg_dump` into `~/fairness-backups/`, rehearses the migrations on a restored copy in a throwaway container,
and migrates the real database only after you type its host name. Stop the gateway first: nothing else should
write while it runs.

**Undoing a migration** means restoring the backup into an empty schema (`pg_restore --clean` alone would keep
the tables the migration added):

```bash
psql -d <database> -c 'drop schema public cascade; create schema public'
pg_restore --no-owner --no-acl -d <database> ~/fairness-backups/<file>.dump
```

**The scorer's login** is the read-only role from `ops/sql/create_scorer_readonly_role.sql`. The scorer's
`/health` only checks that it can connect, so a missing grant shows up as failing score requests with
"permission denied" in the scorer's log: grant `SELECT` on that view to `fairness_scorer`.

## Accounts

| Situation | What to do |
|---|---|
| No users yet | Set `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` (12+ characters) and start the gateway; that admin must change the password at first sign-in. Remove the password from the environment afterwards |
| Locked out after 5 wrong passwords | Wait 15 minutes, or an admin presses **Unlock** on `/admin/users` |
| Forgotten password | An admin presses **Reset password**: it shows a one-time password once, and the user must change it at sign-in. Their other sessions end |
| Someone leaves | An admin disables or deletes their login on `/admin/users`. Refresh is refused at once; an access token they already hold works until it expires (at most 15 minutes) |
| "Too many sign-in attempts" | 10 per minute per client IP. Behind a proxy, check `FORWARD_HEADERS_STRATEGY` so the client IP isn't the proxy's |

The audit log (`/admin/audit`) records sign-ins, lockouts, user changes, syncs, webhook replays and report
downloads.

## Secrets

| Secret | Rotating it |
|---|---|
| `JWT_SECRET` (gateway) | Change and restart. Everyone is signed out (their tokens no longer verify) and signs in again |
| `SCORER_API_KEY` (both) | Set the same new value on both and restart both. Until both have it, scores answer 503 and the gateway logs "Scorer rejected our API key" |
| `PAGERDUTY_WEBHOOK_SECRET` | Change it in PagerDuty's webhook subscription and in the gateway together. Deliveries signed with the other secret are refused with 401; the scheduled sync picks up those incidents |
| `PAGERDUTY_API_TOKEN` | Create the new token in PagerDuty, set it, restart, then revoke the old one |
| Database passwords | Change in PostgreSQL, then in `AZURE_DB_PASSWORD` (gateway) and `AZURE_DB_URL` (scorer), and restart |

Secrets live only in each service's environment (`.env` locally, App Service settings in Azure). `.env` files are
git-ignored and never copied into images.

## When something goes wrong

**Fairness, burnout or trends missing on the analytics page** ("scores unavailable"): the scorer isn't
answering. Counts, charts and the heatmap still work, as they come from the gateway's own SQL. Check the scorer's
`/health`; a 503 there with a database error means it can't reach PostgreSQL. Engineer reports need the scorer
and fail until it's back; analytics reports still download, saying what's missing.

**PagerDuty sync failing:** `/admin/sync` shows each run, FAILED ones with the error. Fix the cause (token,
network, PagerDuty status), then press **Sync now**. A 409 `SYNC_IN_PROGRESS` means that kind of sync is already
running. If an instance died holding the lock, it frees itself after `PAGERDUTY_SYNC_LOCK_AT_MOST_FOR` (30
minutes). To free it sooner, set `lock_until = now()` on its row in `shedlock`, but never delete the row:
ShedLock remembers the lock names it has seen and only updates their rows.

**A webhook failed:** it's kept as FAILED with the error, and PagerDuty's own retry applies it again. Once the
cause is fixed, **Replay** on `/admin/sync` re-applies it. Events that were never delivered are picked up by the
next scheduled sync (incidents from the last `PAGERDUTY_SYNC_LOOKBACK_DAYS` days).

**A report fails:** the user's `/reports` page shows it as failed with the reason, from its `report_export`
row. Windows longer than `REPORT_MAX_WINDOW_DAYS` are refused up front. Behind another proxy, allow at least
two minutes for the response, as Nginx does.

**Signed out on every reload over plain HTTP:** the refresh cookie is `Secure`, and browsers drop it on plain
HTTP except on `localhost`. Serve over HTTPS, or for a demo on another host set `REFRESH_COOKIE_SECURE=false`.

**Gateway won't start:**
- "Schema-validation" errors: a migration is missing (run `ops/migrate-db.sh`) or the image is older than the
  database.
- A missing `JWT_SECRET` or `SCORER_API_KEY`, or one under 32 characters, stops it at startup with a message
  naming the setting.
