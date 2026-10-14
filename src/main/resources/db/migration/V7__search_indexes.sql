-- Search on the alert and engineer lists (the q parameter): case-insensitive "contains" on alert titles, service
-- names and engineer names and emails. Trigram indexes keep that fast however many rows there are.
--
-- pg_trgm ships with PostgreSQL, but managed services allow it only once it's on their list (Azure Database for
-- PostgreSQL flexible server: add PG_TRGM to the azure.extensions server parameter). Without it this migration
-- still succeeds and search still works, by scanning; run the statements below by hand once it's allowed.

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_trgm is not available (%); search works without its indexes', SQLERRM;
END
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        -- The expressions match the queries exactly (lower(column) LIKE '%term%'), or the planner won't use them
        CREATE INDEX IF NOT EXISTS ix_alert_event_title_trgm ON alert_event USING gin (lower(title) gin_trgm_ops);
        CREATE INDEX IF NOT EXISTS ix_service_name_trgm ON service USING gin (lower(name) gin_trgm_ops);
        CREATE INDEX IF NOT EXISTS ix_engineer_data_name_trgm ON engineer_data USING gin (lower(name) gin_trgm_ops);
        CREATE INDEX IF NOT EXISTS ix_engineer_data_email_trgm ON engineer_data USING gin (lower(email) gin_trgm_ops);
    END IF;
END
$$;
