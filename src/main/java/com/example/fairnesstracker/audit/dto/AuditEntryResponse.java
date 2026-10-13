package com.example.fairnesstracker.audit.dto;

import com.example.fairnesstracker.audit.AuditAction;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/** One audit entry; {@code userId}/{@code actorEmail} are null for the system. */
public record AuditEntryResponse(Long id, Instant occurredAt, @Nullable Long userId, @Nullable String actorEmail, AuditAction action,
                                 @Nullable String entityType, @Nullable String entityId, @Nullable Map<String, Object> details, @Nullable String ip,
                                 @Nullable String requestId) {
}
