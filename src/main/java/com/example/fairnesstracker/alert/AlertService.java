package com.example.fairnesstracker.alert;

import com.example.fairnesstracker.alert.dto.AlertFilter;
import com.example.fairnesstracker.alert.dto.AlertRequest;
import com.example.fairnesstracker.alert.dto.AlertResponse;
import com.example.fairnesstracker.common.exception.ResourceNotFoundException;
import com.example.fairnesstracker.common.exception.ValidationException;
import com.example.fairnesstracker.common.web.PageResponse;
import com.example.fairnesstracker.common.web.Sorting;
import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class AlertService {

    static final Set<String> SORTABLE = Set.of("triggeredAt", "resolvedAt", "severity", "status");

    private final AlertRepository alertRepository;
    private final EngineerRepository engineerRepository;
    private final AlertAssignmentRepository assignmentRepository;
    private final AlertMapper alertMapper;

    public AlertService(AlertRepository alertRepository, EngineerRepository engineerRepository,
                        AlertAssignmentRepository assignmentRepository, AlertMapper alertMapper) {
        this.alertRepository = alertRepository;
        this.engineerRepository = engineerRepository;
        this.assignmentRepository = assignmentRepository;
        this.alertMapper = alertMapper;
    }

    /** A manually reported alert, paged to the given engineer now. */
    public AlertResponse saveAlert(AlertRequest request) {

        // A missing engineer is a problem with the request body, not a missing resource at this URL
        Engineer engineer = engineerRepository
                .findById(request.engineerId())
                .orElseThrow(() -> ValidationException.onField("engineerId",
                        "Engineer " + request.engineerId() + " doesn't exist"));

        AlertEvent alert = new AlertEvent();
        alert.setEngineer(engineer);
        alert.setSeverity(request.severity());
        // Microseconds, as PostgreSQL stores them, so this response matches every later read
        alert.setTriggeredAt(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        alert.setSource("MANUAL");
        alert.setPagerDutyUserId(engineer.getPagerDutyUserId());
        alert.setAssignedEngineerName(engineer.getName());
        AlertEvent saved = alertRepository.save(alert);

        assignmentRepository.save(new AlertAssignment(saved, engineer, engineer.getPagerDutyUserId(),
                AlertAssignment.Kind.PAGED, saved.getTriggeredAt(), AlertAssignment.Source.MANUAL));
        return alertMapper.toResponse(saved);
    }

    /** One page of alerts matching every filter that is set; newest first unless sorted otherwise. */
    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> list(AlertFilter filter, Pageable pageable) {
        if (filter.start() != null && filter.end() != null && filter.end().isBefore(filter.start())) {
            throw ValidationException.onField("end", "must be on or after start");
        }
        Page<AlertEvent> page = alertRepository.findAll(AlertSpecifications.matching(filter),
                Sorting.restrict(pageable, SORTABLE));
        return PageResponse.from(page.map(alertMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public AlertResponse getById(Long id) {
        return alertMapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<AlertResponse.Assignment> getAssignments(Long alertId) {
        find(alertId);
        return assignmentRepository.findByAlert_IdOrderByAssignedAtAsc(alertId).stream()
                .map(alertMapper::toResponse)
                .toList();
    }

    public void deleteEvent(Long id){
        alertRepository.delete(find(id));
    }

    private AlertEvent find(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Alert", id));
    }
}
