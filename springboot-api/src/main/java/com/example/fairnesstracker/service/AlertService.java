package com.example.fairnesstracker.service;

import com.example.fairnesstracker.dto.alert.AlertRequest;
import com.example.fairnesstracker.dto.alert.AlertResponse;
import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.exceptions.ResourceNotFoundException;
import com.example.fairnesstracker.repository.EngineerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.repository.AlertRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertService {
    private final AlertRepository alertRepository;
    private final EngineerRepository engineerRepository;

    @Autowired
    public AlertService(AlertRepository alertRepository, EngineerService engineerService, EngineerRepository engineerRepository) {
        this.alertRepository = alertRepository;
        this.engineerRepository = engineerRepository;
    }

    public AlertEvent saveAlert(AlertRequest request) {

        Engineer engineer = engineerRepository
                .findById(request.engineerId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Engineer not found"));

        AlertEvent alert = new AlertEvent();

        alert.setEngineer(engineer);
        alert.setSeverity(request.severity());
        alert.setTriggeredAt(LocalDateTime.now());

        return alertRepository.save(alert);
    }

    public List<AlertEvent> getAllEvents(){
        return alertRepository.findAll();
    }

    public AlertEvent getById(Long id) {

        return alertRepository.findById(id)
                .orElseThrow(()->
                        new RuntimeException(
                                "Event Not Found with id:"+id)
                );
    }


    public List<AlertResponse> getAlertsByEngineer(Long engineerId) {

        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Engineer not found"));

        return alertRepository.findByEngineer_Id(engineerId)
                .stream()
                .map(alert -> new AlertResponse(
                        alert.getId(),
                        alert.getEngineer().getName(),
                        alert.getSeverity(),
                        alert.getTriggeredAt(),
                        alert.getResolvedAt(),
                        alert.getPagerDutyIncidentId(),
                        alert.getIncidentNumber(),
                        alert.getTitle(),
                        alert.getServiceName(),
                        alert.getAssignedEngineerName(),
                        alert.getUrgency(),
                        alert.getSource()
                ))
                .toList();
    }

    public void deleteEvent(Long id){

        AlertEvent alertEvent = alertRepository.findById(id)
                .orElseThrow(()->
                        new RuntimeException(
                                "Event Not Found with id:"+id)
                );
        alertRepository.delete(alertEvent);

    }

    public List<AlertEvent> filterAlerts(
            Long engineerId,
            String severity,
            LocalDateTime from,
            LocalDateTime to
    ){
        return alertRepository.filterAlerts(
                engineerId,
                severity,
                from,
                to
        );
    }

}
