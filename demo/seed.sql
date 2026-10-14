-- Demo data, loaded by seed.sh once the gateway has created the schema: real incidents from two published logs
-- (Volvo IT and a ServiceNow instance; seed.sh builds /tmp/real-data.sql from them with import_real_data.py),
-- plus demo logins and some sync history. Runs only into an empty database, so restarting the demo keeps what's
-- there; `docker compose down -v` starts again from scratch.

\set ON_ERROR_STOP on

SELECT EXISTS (SELECT 1 FROM team) AS seeded \gset
\if :seeded
  \echo 'Demo data already loaded; nothing to do.'
  \quit
\endif

BEGIN;

-- The scorer reads four views; V3 grants it two of them, these are the others
GRANT SELECT ON scoring_alert_v, scoring_engineer_v, scoring_assignment_v, scoring_oncall_v TO fairness_scorer;

COMMIT;

-- Real incident data (from import_real_data.py); it has its own transaction
\i /tmp/real-data.sql

BEGIN;

-- --- Logins -------------------------------------------------------------------------------------------------
-- Demo passwords (also in compose.yaml): Demo-Admin-2026, Demo-Manager-2026, Demo-Engineer-2026
-- The engineer login is the Volvo G92 engineer (an India-based team) with the most alerts
INSERT INTO app_user (email, password_hash, full_name, role, engineer_id, must_change_password)
SELECT 'admin@example.com', '{bcrypt}$2a$12$T.64xFkd2RHExQlTZP8FiOZMiL87Lo.bx3VkxrV7P6Jk67gldC/ti', 'Demo Admin', 'ADMIN',
       NULL::bigint, false
UNION ALL
SELECT 'manager@example.com', '{bcrypt}$2a$12$zx8TMnNasIqUobsnfxAsY.zeAjER6g3/U4N8YrKnbwvQT6krKtave', 'Maya Menon',
       'MANAGER', NULL, false
UNION ALL
(SELECT 'engineer@example.com', '{bcrypt}$2a$12$bJcIWMoXQJGRZQTiizU2WOQfUd/Z6ltYq6mPHv0FESnWARw.2pTNG', e.name,
        'ENGINEER', e.id, false
 FROM engineer_data e JOIN team t ON t.id = e.team_id JOIN alert_event a ON a.engineer_id = e.id
 WHERE t.name = 'Volvo G92' GROUP BY e.id, e.name ORDER BY count(*) DESC, e.id LIMIT 1);

-- The manager looks after the two India-based Volvo teams
INSERT INTO user_team_access (user_id, team_id)
SELECT u.id, t.id FROM app_user u CROSS JOIN team t
WHERE u.email = 'manager@example.com' AND t.name IN ('Volvo G92', 'Volvo G230 2nd');

-- --- A little sync history for the admin's PagerDuty page -----------------------------------------------------
INSERT INTO sync_run (kind, trigger_type, status, started_at, finished_at, created_count, updated_count, error)
VALUES
  ('INCIDENTS', 'SCHEDULED', 'SUCCEEDED', now() - interval '2 hours', now() - interval '2 hours' + interval '4 seconds', 3, 5, NULL),
  ('ONCALLS', 'SCHEDULED', 'SUCCEEDED', now() - interval '2 hours', now() - interval '2 hours' + interval '2 seconds', 6, NULL, NULL),
  ('INCIDENTS', 'SCHEDULED', 'FAILED', now() - interval '50 minutes', now() - interval '50 minutes' + interval '30 seconds', NULL, NULL,
   'WebClientRequestException: PagerDuty did not answer (demo)');

COMMIT;

SELECT t.name AS team, t.timezone, count(DISTINCT e.id) AS engineers, count(a.*) AS alerts,
       count(*) FILTER (WHERE a.triggered_at > now() AT TIME ZONE 'UTC' - interval '30 days') AS last_30_days
FROM team t JOIN engineer_data e ON e.team_id = t.id LEFT JOIN alert_event a ON a.engineer_id = e.id
GROUP BY t.name, t.timezone ORDER BY t.name;
