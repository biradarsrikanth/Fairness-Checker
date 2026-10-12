package com.example.fairnesstracker.audit;

/** What an audit entry records. Stored by name: renaming a constant breaks the history, adding one doesn't. */
public enum AuditAction {
    // Sessions
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    LOGOUT,
    PASSWORD_CHANGED,
    REFRESH_TOKEN_REUSED,
    // Users (by an admin, or the bootstrap at startup)
    USER_CREATED,
    USER_UPDATED,
    USER_TEAMS_CHANGED,
    USER_PASSWORD_RESET,
    USER_UNLOCKED,
    USER_DELETED,
    // PagerDuty operations started by a person
    SYNC_TRIGGERED,
    INCIDENT_RESOLVED
}
