package com.example.fairnesstracker.common.web;

import com.example.fairnesstracker.common.exception.ValidationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/**
 * Each list endpoint names the fields it can sort by: clients can't sort by arbitrary or nested columns
 * (slow without an index, and a typo would otherwise be a 500).
 */
public final class Sorting {

    private Sorting() {
    }

    public static Pageable restrict(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw ValidationException.onField("sort", "can't sort by '" + order.getProperty() + "'; use one of "
                        + String.join(", ", new TreeSet<>(allowed)));
            }
        }
        return pageable;
    }
}
