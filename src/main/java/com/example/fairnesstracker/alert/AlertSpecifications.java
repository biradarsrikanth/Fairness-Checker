package com.example.fairnesstracker.alert;

import com.example.fairnesstracker.alert.dto.AlertFilter;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

// Each filter that is set adds one condition; none set means every alert
final class AlertSpecifications {

    private AlertSpecifications() {
    }

    /** {@code start} and {@code end} are inclusive days in {@code zone}, as on the analytics pages. */
    static Specification<AlertEvent> matching(AlertFilter filter, ZoneId zone) {
        List<Specification<AlertEvent>> conditions = new ArrayList<>();
        if (filter.engineerId() != null) {
            conditions.add((alert, query, cb) -> cb.equal(alert.get("engineer").get("id"), filter.engineerId()));
        }
        if (filter.team() != null && !filter.team().isBlank()) {
            conditions.add((alert, query, cb) ->
                    cb.equal(alert.get("engineer").get("team").get("name"), filter.team().strip()));
        }
        if (filter.severity() != null) {
            conditions.add((alert, query, cb) -> cb.equal(alert.get("severity"), filter.severity()));
        }
        if (filter.status() != null) {
            conditions.add((alert, query, cb) -> cb.equal(alert.get("status"), filter.status()));
        }
        if (filter.start() != null) {
            conditions.add((alert, query, cb) ->
                    cb.greaterThanOrEqualTo(alert.get("triggeredAt"), utc(filter.start(), zone)));
        }
        if (filter.end() != null) {
            // Inclusive end date: everything before the next day's midnight
            conditions.add((alert, query, cb) ->
                    cb.lessThan(alert.get("triggeredAt"), utc(filter.end().plusDays(1), zone)));
        }
        return Specification.allOf(conditions);
    }

    // Times are stored as UTC
    private static LocalDateTime utc(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
