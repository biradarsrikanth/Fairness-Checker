SELECT id, name, pager_duty_user_id
FROM engineer_data;

SELECT
    pager_duty_incident_id,
    assigned_engineer_name,
    pager_duty_user_id,
    engineer_id
FROM alert_event
ORDER BY id DESC
LIMIT 5;

DELETE
FROM alert_event
WHERE source= 'API'