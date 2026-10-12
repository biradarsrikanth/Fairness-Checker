package com.example.fairnesstracker.alert;

import com.example.fairnesstracker.alert.dto.AlertFilter;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

// Each filter that is set adds one condition; none set means every alert
final class AlertSpecifications {

    private AlertSpecifications() {
    }

    static Specification<AlertEvent> matching(AlertFilter filter) {
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
                    cb.greaterThanOrEqualTo(alert.get("triggeredAt"), filter.start().atStartOfDay()));
        }
        if (filter.end() != null) {
            // Inclusive end date: everything before the next day's midnight
            conditions.add((alert, query, cb) ->
                    cb.lessThan(alert.get("triggeredAt"), filter.end().plusDays(1).atStartOfDay()));
        }
        return Specification.allOf(conditions);
    }
}
