#!/usr/bin/env bash
# Loads demo/real-data.sql (real incident data from demo/import_real_data.py) into an existing database, safely:
#   1. checks the schema is new enough and shows what's there
#   2. backs the database up with pg_dump (custom format, public schema)
#   3. after you type the host name to confirm, loads the data in one transaction
#
# The SQL only adds rows (ON CONFLICT DO NOTHING) and creates no logins, so existing data is untouched and a
# second run adds nothing. Undo = restore the backup (see docs/RUNBOOK.md).
#
# Usage (from the repository root):
#   demo/load-real-data.sh                    # database settings from .env
#   ENV_FILE=path/.env BACKUP_DIR=~/somewhere demo/load-real-data.sh
#   DOCKER_NETWORK=<network> ...              # when the database is a container on a Docker network
#
# Needs: docker (psql and pg_dump come from the official postgres image).
set -euo pipefail

cd "$(dirname "$0")"
ENV_FILE="${ENV_FILE:-../.env}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/fairness-backups}"
SQL=real-data.sql

die() { echo "error: $*" >&2; exit 1; }
step() { echo; echo "== $*"; }

[ -s "$SQL" ] || die "no $SQL; run python3 -I demo/import_real_data.py first"
command -v docker >/dev/null || die "docker is required"

# .env, read as data (never sourced); real environment variables win, as they do for the app
if [ -f "$ENV_FILE" ]; then
  while IFS= read -r line; do
    line="${line%$'\r'}"
    key="${line%%=*}" value="${line#*=}"
    [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]] && value="${BASH_REMATCH[1]}"
    [ -n "${!key+set}" ] || export "$key=$value"
  done < <(grep -E '^AZURE_DB_(URL|USERNAME|PASSWORD)=' "$ENV_FILE")
fi
: "${AZURE_DB_URL:?not set}" "${AZURE_DB_USERNAME:?not set}" "${AZURE_DB_PASSWORD:?not set}"

# jdbc:postgresql://host[:port]/database[?params]
[[ "$AZURE_DB_URL" =~ ^jdbc:postgresql://([^/:?]+)(:([0-9]+))?/([^?]+) ]] \
  || die "AZURE_DB_URL isn't a jdbc:postgresql:// URL"
DB_HOST="${BASH_REMATCH[1]}"; DB_PORT="${BASH_REMATCH[3]:-5432}"; DB_NAME="${BASH_REMATCH[4]}"

mkdir -p "$BACKUP_DIR"
pg() {
  local image="$1"; shift
  docker run --rm --user "$(id -u):$(id -g)" ${DOCKER_NETWORK:+--network "$DOCKER_NETWORK"} \
    -e PGPASSWORD="$AZURE_DB_PASSWORD" -e PGSSLMODE="${PGSSLMODE:-prefer}" \
    -v "$BACKUP_DIR:/backup" -v "$PWD/$SQL:/data/$SQL:ro" "$image" "$@"
}
db_psql() {
  pg postgres:17-alpine psql -h "$DB_HOST" -p "$DB_PORT" -U "$AZURE_DB_USERNAME" -d "$DB_NAME" \
    -v ON_ERROR_STOP=1 -qAt "$@"
}
COUNTS="select 'teams ' || (select count(*) from team) || ', engineers ' || (select count(*) from engineer_data)
  || ', alerts ' || (select count(*) from alert_event) || ', paging steps ' || (select count(*) from alert_assignment)"

echo "database: $DB_NAME on $DB_HOST:$DB_PORT as $AZURE_DB_USERNAME"
sed -n 's/^-- \(.*: [0-9]* teams.*\)/data:     \1/p' "$SQL"

step "Schema and current state"
VERSION="$(db_psql -c "select coalesce(max(version::int), 0) from flyway_schema_history where success" 2>/dev/null)" \
  || die "no flyway_schema_history: start the gateway against this database once first"
[ "$VERSION" -ge 3 ] || die "schema is at V$VERSION; the data needs V3 or later (run ops/migrate-db.sh)"
echo "   schema V$VERSION"
echo "   now: $(db_psql -c "$COUNTS")"
if [ "$(db_psql -c "select exists (select 1 from alert_event where pager_duty_incident_id like 'VOLVO-%' or pager_duty_incident_id like 'SN-%')")" = t ]; then
  echo "   some of this data is already there: only missing rows will be added"
fi

step "Backup"
SERVER_MAJOR="$(db_psql -c 'show server_version_num' | cut -c1-2)"
BACKUP="$BACKUP_DIR/${DB_HOST%%.*}-$DB_NAME-before-real-data-$(date +%Y%m%d-%H%M%S).dump"
pg "postgres:$SERVER_MAJOR-alpine" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$AZURE_DB_USERNAME" -d "$DB_NAME" \
  -n public --no-owner --no-acl -Fc -f "/backup/$(basename "$BACKUP")"
[ -s "$BACKUP" ] || die "backup is empty"
echo "   $BACKUP ($(du -h "$BACKUP" | cut -f1))"

step "Load into $DB_HOST"
read -rp "   Type the host name to continue: " answer
[ "$answer" = "$DB_HOST" ] || die "host name didn't match; nothing was loaded"
db_psql -f "/data/$SQL" > /dev/null
echo "   after: $(db_psql -c "$COUNTS")"
echo
echo "Done. The scorer caches results for up to a minute; new figures show after that."
