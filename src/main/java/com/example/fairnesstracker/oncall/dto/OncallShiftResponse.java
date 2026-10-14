package com.example.fairnesstracker.oncall.dto;

import java.time.Instant;

/** Level 1 is the primary on call; higher levels are escalation backups. */
public record OncallShiftResponse(Long id, Long engineerId, String engineerName, String team, int escalationLevel,
                                  Instant startsAt, Instant endsAt) {
}
