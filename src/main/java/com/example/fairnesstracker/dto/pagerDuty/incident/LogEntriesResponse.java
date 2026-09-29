package com.example.fairnesstracker.dto.pagerDuty.incident;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

// GET /incidents/{id}/log_entries: the source of truth for who was paged, escalated to and who acknowledged
@Data
public class LogEntriesResponse {

    @JsonProperty("log_entries")
    private List<LogEntry> logEntries;

    @Data
    public static class LogEntry {
        // e.g. assign_log_entry, escalate_log_entry, acknowledge_log_entry, resolve_log_entry
        private String type;

        @JsonProperty("created_at")
        private String createdAt;

        // Set on assign and escalate entries
        private List<Assignee> assignees;

        // Some escalate entries name a single user instead
        @JsonProperty("assigned_user")
        private Assignee assignedUser;

        // Who performed the action, e.g. the user who acknowledged
        private LastStatusChangeBy agent;
    }
}
