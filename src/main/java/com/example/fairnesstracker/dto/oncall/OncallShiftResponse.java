package com.example.fairnesstracker.dto.oncall;

import com.example.fairnesstracker.entity.OncallShift;

import java.time.LocalDateTime;

// Times are UTC
public record OncallShiftResponse(Long id, Long engineerId, String engineerName, int escalationLevel,
                                  LocalDateTime startsAt, LocalDateTime endsAt) {

    public static OncallShiftResponse from(OncallShift shift) {
        return new OncallShiftResponse(shift.getId(), shift.getEngineer().getId(), shift.getEngineer().getName(),
                shift.getEscalationLevel(), shift.getStartsAt(), shift.getEndsAt());
    }
}
