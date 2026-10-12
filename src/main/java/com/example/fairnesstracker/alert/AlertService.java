package com.example.fairnesstracker.alert;

import com.example.fairnesstracker.alert.dto.AlertRequest;
import com.example.fairnesstracker.alert.dto.AlertResponse;
import com.example.fairnesstracker.common.exception.ResourceNotFoundException;
import com.example.fairnesstracker.common.exception.ValidationException;
import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Transactional
public class AlertService {
    private final AlertRepository alertRepository;
    private final EngineerRepository engineerRepository;
    private final AlertAssignmentRepository assignmentRepository;

    public AlertService(AlertRepository alertRepository, EngineerRepository engineerRepository,
                        AlertAssignmentRepository assignmentRepository) {
        this.alertRepository = alertRepository;
        this.engineerRepository = engineerRepository;
        this.assignmentRepository = assignmentRepository;
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
        alert.setTriggeredAt(LocalDateTime.now(ZoneOffset.UTC));
        alert.setSource("MANUAL");
        alert.setPagerDutyUserId(engineer.getPagerDutyUserId());
        alert.setAssignedEngineerName(engineer.getName());
        AlertEvent saved = alertRepository.save(alert);

        assignmentRepository.save(new AlertAssignment(saved, engineer, engineer.getPagerDutyUserId(),
                AlertAssignment.Kind.PAGED, saved.getTriggeredAt(), AlertAssignment.Source.MANUAL));
        return AlertResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAllEvents(){
        return alertRepository.findAll().stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AlertResponse getById(Long id) {
        return AlertResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<AlertResponse.Assignment> getAssignments(Long alertId) {
        find(alertId);
        return assignmentRepository.findByAlert_IdOrderByAssignedAtAsc(alertId).stream()
                .map(AlertResponse.Assignment::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlertsByEngineer(Long engineerId) {

        engineerRepository.findById(engineerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Engineer", engineerId));

        return alertRepository.findByEngineer_Id(engineerId)
                .stream()
                .map(AlertResponse::from)
                .toList();
    }

    public void deleteEvent(Long id){
        alertRepository.delete(find(id));
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> filterAlerts(
            Long engineerId,
            String severity,
            LocalDateTime from,
            LocalDateTime to
    ){
        return alertRepository.filterAlerts(engineerId, severity, from, to)
                .stream()
                .map(AlertResponse::from)
                .toList();
    }

    private AlertEvent find(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Alert", id));
    }
}
