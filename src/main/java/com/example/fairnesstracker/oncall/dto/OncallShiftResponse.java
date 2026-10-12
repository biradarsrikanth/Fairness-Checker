package com.example.fairnesstracker.oncall.dto;

import com.example.fairnesstracker.oncall.OncallShift;

import java.time.LocalDateTime;

// Times are UTC
public record OncallShiftResponse(Long id, Long engineerId, String engineerName, int escalationLevel,
                                  LocalDateTime startsAt, LocalDateTime endsAt) {

    public static OncallShiftResponse from(OncallShift shift) {
        return new OncallShiftResponse(shift.getId(), shift.getEngineer().getId(), shift.getEngineer().getName(),
                shift.getEscalationLevel(), shift.getStartsAt(), shift.getEndsAt());
    }
}
