package com.example.fairnesstracker.audit;

import com.example.fairnesstracker.audit.dto.AuditEntryResponse;
import com.example.fairnesstracker.audit.dto.AuditFilter;
import com.example.fairnesstracker.common.exception.ValidationException;
import com.example.fairnesstracker.common.web.PageResponse;
import com.example.fairnesstracker.common.web.Sorting;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.SortDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Tag(name = "Audit", description = "Who did what: sign-ins, user changes, manual PagerDuty operations (admins only)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private static final Set<String> SORTABLE = Set.of("occurredAt");

    private final AuditLogRepository repository;
    private final AuditMapper mapper;

    public AuditController(AuditLogRepository repository, AuditMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Operation(operationId = "listAuditEntries", summary = "List audit entries", description = "Newest first. Filters combine with AND.")
    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> list(
            @Valid @ParameterObject AuditFilter filter,
            @ParameterObject @SortDefault(sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (filter.start() != null && filter.end() != null && filter.end().isBefore(filter.start())) {
            throw ValidationException.onField("end", "must be on or after start");
        }
        List<Specification<AuditLog>> conditions = new ArrayList<>();
        if (filter.action() != null) {
            conditions.add((entry, query, cb) -> cb.equal(entry.get("action"), filter.action()));
        }
        if (filter.userId() != null) {
            conditions.add((entry, query, cb) -> cb.equal(entry.get("userId"), filter.userId()));
        }
        if (filter.start() != null) {
            conditions.add((entry, query, cb) -> cb.greaterThanOrEqualTo(entry.get("occurredAt"),
                    filter.start().atStartOfDay().toInstant(ZoneOffset.UTC)));
        }
        if (filter.end() != null) {
            conditions.add((entry, query, cb) -> cb.lessThan(entry.get("occurredAt"),
                    filter.end().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        }
        return PageResponse.from(repository.findAll(Specification.allOf(conditions),
                Sorting.restrict(pageable, SORTABLE)).map(mapper::toResponse));
    }
}
