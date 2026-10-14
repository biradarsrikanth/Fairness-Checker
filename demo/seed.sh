#!/bin/sh
# Loads the demo data into an empty database: downloads the two published incident logs, turns them into SQL
# (import_real_data.py, dates moved so the latest incidents are from the past week), then runs seed.sql.
# Does nothing when the database already has data, so restarting the demo keeps what's there.
set -eu

# The database password the secrets job generated
PGPASSWORD="$(cat /run/secrets/AZURE_DB_PASSWORD)"
export PGPASSWORD

if [ "$(psql -tAc 'SELECT EXISTS (SELECT 1 FROM team)')" = t ]; then
    echo 'Demo data already loaded; nothing to do.'
    exit 0
fi

python3 /demo/import_real_data.py --out /tmp/real-data.sql
psql -q -f /demo/seed.sql
