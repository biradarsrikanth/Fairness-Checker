package com.example.fairnesstracker.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.repository.AlertRepository;

import java.util.List;
import java.util.Optional;

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

    public Optional<AlertEvent> getById(Long id) {
        return alertRepository.findById(id);
    }

    public void deleteEvent(Long id){
        alertRepository.deleteById(id);
    }

}
