package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping
public class AlertEventController {

    private final AlertService alertService;

    @Autowired
    public AlertEventController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping("/alert")
    public ResponseEntity<AlertEvent> saveAlert(@RequestBody AlertEvent alertEvent){
        AlertEvent newAlert=alertService.saveAlert(alertEvent);
        return ResponseEntity.ok(alertEvent);
    }

    @GetMapping("/alerts")
    public List<AlertEvent> getAllEvents(){
        return alertService.getAllEvents();
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity<AlertEvent> getById(@PathVariable Long id){
        Optional<AlertEvent> alertEvent=alertService.getById(id);
        return alertEvent.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("alerts/{id}")
    public ResponseEntity<String> deleteEvent(@PathVariable Long id){
        alertService.deleteEvent(id);
        return ResponseEntity.ok("Event SuccessFully Deleted!");
    }


}
