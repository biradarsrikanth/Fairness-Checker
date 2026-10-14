#!/bin/sh
# Runs once, when the demo database is first created (before the gateway's migrations): the scorer's read-only
# login, as in production, with the password the secrets job generated. The migrations grant it the scoring
# views when it exists.
set -eu

psql -v ON_ERROR_STOP=1 -v pw="$(cat /run/secrets/SCORER_DB_PASSWORD)" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE fairness_scorer LOGIN PASSWORD :'pw';
ALTER ROLE fairness_scorer SET default_transaction_read_only = on;
GRANT CONNECT ON DATABASE fairness TO fairness_scorer;
GRANT USAGE ON SCHEMA public TO fairness_scorer;
SQL
