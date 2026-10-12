package com.example.fairnesstracker.audit.dto;

import com.example.fairnesstracker.audit.AuditAction;

import java.time.Instant;
import java.util.Map;

/** One audit entry; {@code userId}/{@code actorEmail} are null for the system. */
public record AuditEntryResponse(Long id, Instant occurredAt, Long userId, String actorEmail, AuditAction action,
                                 String entityType, String entityId, Map<String, Object> details, String ip,
                                 String requestId) {
}
