package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.dto.oncall.OncallShiftResponse;
import com.example.fairnesstracker.exceptions.BadRequestException;
import com.example.fairnesstracker.repository.OncallShiftRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/oncall")
public class OncallController {

    private static final long MAX_DAYS = 366;

    private final OncallShiftRepository shiftRepository;

    public OncallController(OncallShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    /** Shifts overlapping [from, to] (UTC dates, inclusive), optionally for one engineer. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<OncallShiftResponse> shifts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long engineerId) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new BadRequestException("'to' must be on or after 'from', at most " + MAX_DAYS + " days later");
        }
        return shiftRepository.findInPeriod(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), engineerId)
                .stream()
                .map(OncallShiftResponse::from)
                .toList();
    }
}
