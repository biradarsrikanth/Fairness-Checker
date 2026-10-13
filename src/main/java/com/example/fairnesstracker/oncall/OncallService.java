package com.example.fairnesstracker.oncall;

import com.example.fairnesstracker.analytics.AnalyticsProperties;
import com.example.fairnesstracker.security.DataScope;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
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
    // Dates are days here, as on the analytics pages; times are stored as UTC
    private final ZoneId zone;

    public OncallService(OncallShiftRepository shiftRepository, OncallShiftMapper shiftMapper,
                         AnalyticsProperties analytics) {
        this.zone = analytics.zone();
        this.shiftRepository = shiftRepository;
        this.shiftMapper = shiftMapper;
    }

    /** Shifts {@code scope} may see overlapping [start, end] (inclusive days in the analytics timezone), optionally for one engineer. */
    public PageResponse<OncallShiftResponse> shifts(LocalDate start, LocalDate end, Long engineerId,
                                                    Pageable pageable, DataScope scope) {
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            throw ValidationException.onField("end", "must be on or after start, at most " + MAX_DAYS + " days later");
        }
        LocalDateTime from = start.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime until = end.plusDays(1).atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        List<Specification<OncallShift>> conditions = new ArrayList<>();
        // Overlaps the window: starts before it ends and ends after it starts
        conditions.add((shift, query, cb) -> cb.and(
                cb.lessThan(shift.get("startsAt"), until),
                cb.greaterThan(shift.get("endsAt"), from)));
        if (engineerId != null) {
            conditions.add((shift, query, cb) -> cb.equal(shift.get("engineer").get("id"), engineerId));
        }
        conditions.add(scope.restrict(shift -> shift.get("engineer")));
        return PageResponse.from(shiftRepository.findAll(Specification.allOf(conditions),
                Sorting.restrict(pageable, SORTABLE)).map(shiftMapper::toResponse));
    }
}
