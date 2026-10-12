#!/usr/bin/env bash
# Applies pending Flyway migrations to a real database, safely:
#   1. backs the database up with pg_dump (custom format, public schema)
#   2. restores that backup into a throwaway PostgreSQL container and migrates the copy first,
#      so a migration that fails on real data fails there, not on the real database
#   3. after you type the host name to confirm, migrates the real database
#
# Migrations run inside the gateway jar itself (web server and PagerDuty sync off), so they use exactly
# the Flyway version and settings production uses, and Hibernate then validates the migrated schema.
# The jar exits when it has started; nothing is served.
#
# Usage (from the repository root, after `sh mvnw -B package`):
#   ops/migrate-db.sh                 # database settings from .env
#   JAR=path/to.jar BACKUP_DIR=~/somewhere ops/migrate-db.sh
#
# Needs: docker, and a JDK 21 at $JAVA_HOME (or java on the PATH).
# Undo = restore the backup into an empty public schema (pg_restore --clean alone would keep tables the
# migrations added):
#   psql -d <database> -c 'drop schema public cascade; create schema public'
#   pg_restore --no-owner --no-acl -d <database> <file.dump>
set -euo pipefail

cd "$(dirname "$0")/.."
JAR="${JAR:-$(ls target/*.jar 2>/dev/null | grep -v '\.original$' | head -1)}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/fairness-backups}"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
REHEARSAL=fairness-migrate-rehearsal
REHEARSAL_PORT=55439

die() { echo "error: $*" >&2; exit 1; }
step() { echo; echo "== $*"; }

[ -n "$JAR" ] && [ -f "$JAR" ] || die "no jar in target/; build it first (sh mvnw -B package) or set JAR"
command -v docker >/dev/null || die "docker is required"
JAR="$(realpath "$JAR")"

# .env, read as data (never sourced); real environment variables win, as they do for the app
if [ -f .env ]; then
  while IFS='=' read -r key value; do
    [[ "$key" =~ ^[A-Z_][A-Z0-9_]*$ ]] || continue
    [ -n "${!key+set}" ] || export "$key=$value"
  done < <(grep -E '^[A-Z_][A-Z0-9_]*=' .env)
fi
: "${AZURE_DB_URL:?not set}" "${AZURE_DB_USERNAME:?not set}" "${AZURE_DB_PASSWORD:?not set}"
: "${SCORER_API_KEY:?not set (the app refuses to start without it)}"

# jdbc:postgresql://host[:port]/database[?params]
[[ "$AZURE_DB_URL" =~ ^jdbc:postgresql://([^/:?]+)(:([0-9]+))?/([^?]+) ]] \
  || die "AZURE_DB_URL isn't a jdbc:postgresql:// URL"
DB_HOST="${BASH_REMATCH[1]}"; DB_PORT="${BASH_REMATCH[3]:-5432}"; DB_NAME="${BASH_REMATCH[4]}"

# psql/pg_dump from the official image, connecting to the real database
pg() {
  local image="$1"; shift
  docker run --rm --user "$(id -u):$(id -g)" -e PGPASSWORD="$AZURE_DB_PASSWORD" -e PGSSLMODE="${PGSSLMODE:-prefer}" \
    -v "$BACKUP_DIR:/backup" "$image" "$@"
}
real_psql() {
  pg postgres:17-alpine psql -h "$DB_HOST" -p "$DB_PORT" -U "$AZURE_DB_USERNAME" -d "$DB_NAME" \
    -v ON_ERROR_STOP=1 -qAt "$@"
}

# Runs the jar against a database; it migrates, validates and exits
migrate() {
  local url="$1" user="$2" password="$3" log="$4"
  # From an empty directory, so no .env is picked up: everything comes from this environment
  (cd "$WORK" && env AZURE_DB_URL="$url" AZURE_DB_USERNAME="$user" AZURE_DB_PASSWORD="$password" \
      PAGERDUTY_SYNC_ENABLED=false PAGERDUTY_API_TOKEN="${PAGERDUTY_API_TOKEN:-unused}" \
      "$JAVA" -jar "$JAR" --spring.main.web-application-type=none > "$log" 2>&1) || {
    grep -E "ERROR|Caused by|Migration .* failed" "$log" | head -20 >&2
    die "migration failed; full log: $log"
  }
  grep -E "Successfully applied|Schema .* is up to date|Migrating schema" "$log" | sed 's/^.*: /   /'
}

COUNTS_SQL="select table_name || ' ' || (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I', table_name), false, true, '')))[1]::text
  from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE' order by 1"
HISTORY_SQL="select coalesce(version, '-') || ' ' || description || ' ' || success from flyway_schema_history order by installed_rank"

WORK="$(mktemp -d)"
trap 'docker rm -f "$REHEARSAL" >/dev/null 2>&1 || true; rm -rf "$WORK"' EXIT
mkdir -p "$BACKUP_DIR"
echo "database: $DB_NAME on $DB_HOST:$DB_PORT as $AZURE_DB_USERNAME"
echo "jar:      $JAR ($(unzip -l "$JAR" | grep -o 'db/migration/V[^ ]*\.sql' | sed 's#.*/##' | tr '\n' ' '))"

step "Is anything else writing to it?"
echo "   Stop the old app first (Azure: az webapp stop -n fairness-tracker -g <resource-group>, or the portal),"
echo "   so it doesn't write rows in the old shape while the schema changes."
read -rp "   Stopped (or nothing else is running)? [y/N] " answer
[ "$answer" = y ] || die "stopped; nothing was changed"

step "Server and current state"
SERVER_MAJOR="$(real_psql -c 'show server_version_num' | cut -c1-2)"
echo "   PostgreSQL $SERVER_MAJOR"
if [ "$(real_psql -c "select to_regclass('public.flyway_schema_history') is not null")" = t ]; then
  real_psql -c "$HISTORY_SQL" | sed 's/^/   applied: /'
else
  echo "   no flyway_schema_history yet: the existing schema will be baselined as V1"
fi

step "Backup"
BACKUP="$BACKUP_DIR/${DB_HOST%%.*}-$DB_NAME-$(date +%Y%m%d-%H%M%S).dump"
pg "postgres:$SERVER_MAJOR-alpine" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$AZURE_DB_USERNAME" -d "$DB_NAME" \
  -n public --no-owner --no-acl -Fc -f "/backup/$(basename "$BACKUP")"
[ -s "$BACKUP" ] || die "backup is empty"
echo "   $BACKUP ($(du -h "$BACKUP" | cut -f1))"

step "Rehearsal on a restored copy"
docker run -d --rm --name "$REHEARSAL" -e POSTGRES_PASSWORD=rehearsal -p "127.0.0.1:$REHEARSAL_PORT:5432" \
  -v "$BACKUP_DIR:/backup:ro" "postgres:$SERVER_MAJOR-alpine" >/dev/null
for _ in $(seq 30); do docker exec "$REHEARSAL" pg_isready -q -U postgres && break; sleep 1; done; sleep 2
docker exec "$REHEARSAL" pg_restore -U postgres -d postgres --clean --if-exists --exit-on-error --no-owner --no-acl "/backup/$(basename "$BACKUP")"
rehearsal_psql() { docker exec "$REHEARSAL" psql -U postgres -d postgres -v ON_ERROR_STOP=1 -qAt "$@"; }
rehearsal_psql -c "$COUNTS_SQL" | LC_ALL=C sort > "$WORK/before.txt"
migrate "jdbc:postgresql://127.0.0.1:$REHEARSAL_PORT/postgres" postgres rehearsal "$WORK/rehearsal.log"
rehearsal_psql -c "$COUNTS_SQL" | LC_ALL=C sort > "$WORK/after.txt"
echo "   rows per table, before → after:"
LC_ALL=C join -a1 -a2 -e '-' -o 0,1.2,2.2 "$WORK/before.txt" "$WORK/after.txt" | awk '{printf "     %-28s %6s → %s\n", $1, $2, $3}'
rehearsal_psql -c "select table_name from information_schema.views where table_schema = 'public' order by 1" \
  | sed 's/^/   view: /'

step "Migrate $DB_HOST"
echo "   The rehearsal passed. This changes the real database (there is no automatic undo; the backup is the undo)."
read -rp "   Type the host name to continue: " answer
[ "$answer" = "$DB_HOST" ] || die "stopped; the real database was not changed"
migrate "$AZURE_DB_URL" "$AZURE_DB_USERNAME" "$AZURE_DB_PASSWORD" "$BACKUP_DIR/migrate-$(date +%Y%m%d-%H%M%S).log"
real_psql -c "$HISTORY_SQL" | sed 's/^/   applied: /'
echo
echo "Done. Backup: $BACKUP"
