package com.example.fairnesstracker.oncall;

import com.example.fairnesstracker.common.exception.ValidationException;
import com.example.fairnesstracker.oncall.dto.OncallShiftResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OncallService {

    static final long MAX_DAYS = 366;

    private final OncallShiftRepository shiftRepository;
    private final OncallShiftMapper shiftMapper;

    public OncallService(OncallShiftRepository shiftRepository, OncallShiftMapper shiftMapper) {
        this.shiftRepository = shiftRepository;
        this.shiftMapper = shiftMapper;
    }

    /** Shifts overlapping [from, to] (UTC dates, inclusive), optionally for one engineer. */
    public List<OncallShiftResponse> shifts(LocalDate from, LocalDate to, Long engineerId) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw ValidationException.onField("to", "must be on or after 'from', at most " + MAX_DAYS + " days later");
        }
        return shiftRepository.findInPeriod(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), engineerId)
                .stream()
                .map(shiftMapper::toResponse)
                .toList();
    }
}
