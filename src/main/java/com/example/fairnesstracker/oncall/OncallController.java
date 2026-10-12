package com.example.fairnesstracker.oncall;

import com.example.fairnesstracker.common.exception.BadRequestException;
import com.example.fairnesstracker.oncall.dto.OncallShiftResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Tag(name = "On-call", description = "On-call shifts synced from PagerDuty")
@RestController
@RequestMapping("/api/v1/oncall")
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
