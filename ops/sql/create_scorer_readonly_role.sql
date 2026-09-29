-- Least-privilege login for fairneess-scorer. Run once as the server admin, after the V3 migration.
-- (If the role already exists, V3 grants it the new views automatically.)
-- Views run with their owner's rights, so the scorer needs SELECT on the views only, not on the tables.
--
--   psql "host=<server>.postgres.database.azure.com dbname=postgres user=<admin> sslmode=require" \
--        -v scorer_password="'<strong-password>'" -f create_scorer_readonly_role.sql
--
-- Then set the scorer's AZURE_DB_URL to
--   postgresql+psycopg://fairness_scorer:<password>@<server>.postgres.database.azure.com:5432/postgres?sslmode=require

CREATE ROLE fairness_scorer LOGIN PASSWORD :scorer_password;
ALTER ROLE fairness_scorer SET default_transaction_read_only = on;

GRANT CONNECT ON DATABASE postgres TO fairness_scorer;
GRANT USAGE ON SCHEMA public TO fairness_scorer;
GRANT SELECT ON scoring_alert_v, scoring_engineer_v, scoring_assignment_v, scoring_oncall_v TO fairness_scorer;
