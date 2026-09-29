-- Engineers who have left the rotation stay for history but no longer count as having zero alerts
ALTER TABLE engineer_data ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- One row per PagerDuty incident: makes sync and webhook upserts idempotent
CREATE UNIQUE INDEX ux_alert_event_pager_duty_incident_id ON alert_event (pager_duty_incident_id);
-- Scoring filters by time window and groups by engineer
CREATE INDEX ix_alert_event_triggered_at ON alert_event (triggered_at);
CREATE INDEX ix_alert_event_engineer_id ON alert_event (engineer_id);

-- Read contract for fairneess-scorer. The scorer reads only these views, never the tables.
-- Columns may be added; renaming or removing one is a breaking change for the scorer.
CREATE VIEW scoring_engineer_v AS
SELECT id AS engineer_id, name, team, active
FROM engineer_data;

CREATE VIEW scoring_alert_v AS
SELECT id AS alert_id, engineer_id, severity, status, triggered_at, resolved_at
FROM alert_event
WHERE engineer_id IS NOT NULL;

COMMENT ON VIEW scoring_engineer_v IS 'Scorer read contract: engineers. See docs/ARCHITECTURE.md';
COMMENT ON VIEW scoring_alert_v IS 'Scorer read contract: attributed alerts. Timestamps are UTC.';
