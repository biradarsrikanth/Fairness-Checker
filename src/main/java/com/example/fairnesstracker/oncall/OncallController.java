package com.example.fairnesstracker.oncall;

import com.example.fairnesstracker.security.AccessPolicy;
import com.example.fairnesstracker.common.web.PageResponse;
import com.example.fairnesstracker.oncall.dto.OncallShiftResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "On-call", description = "On-call shifts synced from PagerDuty")
@RestController
@RequestMapping("/api/v1/oncall")
public class OncallController {

    private final OncallService oncallService;

    private final AccessPolicy access;

    public OncallController(OncallService oncallService, AccessPolicy access) {
        this.oncallService = oncallService;
        this.access = access;
    }

    @Operation(operationId = "listOncallShifts", summary = "List on-call shifts",
            description = "Shifts overlapping [start, end] (UTC dates, inclusive, at most 366 days apart), "
                    + "optionally for one engineer. Sort by startsAt (default) or endsAt.")
    @GetMapping
    public PageResponse<OncallShiftResponse> shifts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) @Positive Long engineerId,
            @ParameterObject @SortDefault("startsAt") Pageable pageable) {
        return oncallService.shifts(start, end, engineerId, pageable, access.scope());
    }
}
