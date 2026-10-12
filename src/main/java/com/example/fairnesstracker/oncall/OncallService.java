package com.example.fairnesstracker.oncall;

import com.example.fairnesstracker.common.exception.ValidationException;
import com.example.fairnesstracker.common.web.PageResponse;
import com.example.fairnesstracker.common.web.Sorting;
import com.example.fairnesstracker.oncall.dto.OncallShiftResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class OncallService {

    static final long MAX_DAYS = 366;
    static final Set<String> SORTABLE = Set.of("startsAt", "endsAt");

    private final OncallShiftRepository shiftRepository;
    private final OncallShiftMapper shiftMapper;

    public OncallService(OncallShiftRepository shiftRepository, OncallShiftMapper shiftMapper) {
        this.shiftRepository = shiftRepository;
        this.shiftMapper = shiftMapper;
    }

    /** Shifts overlapping [start, end] (UTC dates, inclusive), optionally for one engineer. */
    public PageResponse<OncallShiftResponse> shifts(LocalDate start, LocalDate end, Long engineerId,
                                                    Pageable pageable) {
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            throw ValidationException.onField("end", "must be on or after start, at most " + MAX_DAYS + " days later");
        }
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime until = end.plusDays(1).atStartOfDay();

        List<Specification<OncallShift>> conditions = new ArrayList<>();
        // Overlaps the window: starts before it ends and ends after it starts
        conditions.add((shift, query, cb) -> cb.and(
                cb.lessThan(shift.get("startsAt"), until),
                cb.greaterThan(shift.get("endsAt"), from)));
        if (engineerId != null) {
            conditions.add((shift, query, cb) -> cb.equal(shift.get("engineer").get("id"), engineerId));
        }
        return PageResponse.from(shiftRepository.findAll(Specification.allOf(conditions),
                Sorting.restrict(pageable, SORTABLE)).map(shiftMapper::toResponse));
    }
}
