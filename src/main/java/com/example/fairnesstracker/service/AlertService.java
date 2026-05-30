package com.example.fairnesstracker.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.repository.AlertRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertService {
    private final AlertRepository alertRepository;

    @Autowired
    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public AlertEvent saveAlert(AlertEvent alertEvent){
            return alertRepository.save(alertEvent);
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

    public void deleteEvent(Long id){

        AlertEvent alertEvent = alertRepository.findById(id)
                .orElseThrow(()->
                        new RuntimeException(
                                "Event Not Found with id:"+id)
                );
        alertRepository.delete(alertEvent);

    }

    public List<AlertEvent> filterAlerts(
            String engineerId,
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
