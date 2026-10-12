-- Descriptive analytics (counts, averages, distributions) read this view, as the scorer reads its scoring_*
-- views. One row per alert, unassigned ones included, with the alert's local time in its team's timezone.

-- One engineer's alerts over a period: per-engineer charts, scoped lists. Covers the old single-column index.
CREATE INDEX ix_alert_event_engineer_triggered ON alert_event (engineer_id, triggered_at);
DROP INDEX ix_alert_event_engineer_id;

CREATE VIEW analytics_alert_v AS
SELECT a.id                                                       AS alert_id,
       a.engineer_id,
       e.name                                                     AS engineer_name,
       e.team_id,
       t.name                                                     AS team,
       a.service_id,
       s.name                                                     AS service_name,
       a.severity,
       a.status,
       a.triggered_at,
       a.acknowledged_at,
       a.resolved_at,
       local.at                                                   AS triggered_at_local,
       extract(HOUR FROM local.at)::int                           AS hour_local,
       -- ISO day of week: 1 = Monday ... 7 = Sunday
       extract(ISODOW FROM local.at)::int                         AS dow_local,
       extract(EPOCH FROM (a.acknowledged_at - a.triggered_at)) / 60 AS ack_minutes,
       extract(EPOCH FROM (a.resolved_at - a.triggered_at)) / 60    AS resolve_minutes,
       -- Same definition as the scorer: evening and night (18:00-06:00) or a weekend, team-local
       (extract(HOUR FROM local.at) >= 18 OR extract(HOUR FROM local.at) < 6
           OR extract(ISODOW FROM local.at) >= 6)                 AS is_after_hours,
       (SELECT count(*)
        FROM alert_assignment x
        WHERE x.alert_id = a.id AND x.kind = 'ESCALATED')         AS escalation_count
FROM alert_event a
         LEFT JOIN engineer_data e ON e.id = a.engineer_id
         LEFT JOIN team t ON t.id = e.team_id
         LEFT JOIN service s ON s.id = a.service_id
         -- Alerts without a team are read in UTC
         CROSS JOIN LATERAL (SELECT (a.triggered_at AT TIME ZONE 'UTC') AT TIME ZONE coalesce(t.timezone, 'UTC') AS at) local;

COMMENT ON VIEW analytics_alert_v IS
    'Gateway analytics: one row per alert. triggered_at/acknowledged_at/resolved_at are UTC; *_local and '
        'hour_local/dow_local (ISO, 1 = Monday) are in the team''s timezone (UTC without a team). Minutes are '
        'null until the alert is acknowledged/resolved.';
