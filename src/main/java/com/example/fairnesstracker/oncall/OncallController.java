package com.example.fairnesstracker.oncall;

import com.example.fairnesstracker.oncall.dto.OncallShiftResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "On-call", description = "On-call shifts synced from PagerDuty")
@RestController
@RequestMapping("/api/v1/oncall")
public class OncallController {

    private final OncallService oncallService;

    public OncallController(OncallService oncallService) {
        this.oncallService = oncallService;
    }

    /** Shifts overlapping [from, to] (UTC dates, inclusive), optionally for one engineer. */
    @GetMapping
    public List<OncallShiftResponse> shifts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long engineerId) {
        return oncallService.shifts(from, to, engineerId);
    }
}
