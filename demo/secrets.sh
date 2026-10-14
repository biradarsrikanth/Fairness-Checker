#!/bin/sh
# The demo's one-off secrets job: a random value for each password and key the services share, written once
# into the secrets volume and kept from then on (restarts keep everyone signed in). `docker compose down -v`
# forgets them together with the database, so the two always match.
set -eu
cd /run/secrets

for name in AZURE_DB_PASSWORD SCORER_DB_PASSWORD JWT_SECRET SCORER_API_KEY; do
    if [ ! -s "$name" ]; then
        # 48 letters and digits: long enough for every check, and safe inside a database URL
        tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 48 > "$name.tmp"
        mv "$name.tmp" "$name"
        echo "generated $name"
    fi
done
# The gateway and the scorer don't run as root
chmod 444 ./*
