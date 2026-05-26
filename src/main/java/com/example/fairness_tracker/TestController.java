package com.example.fairness_tracker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api")
public class TestController {

    @Autowired
    private AlertRepository alertRepository;

    @PostMapping("/test")
    public AlertEvent createTest() {
        AlertEvent event = new AlertEvent();
        event.setEngineerId("eng-001");
        event.setEngineerName("Arjun");
        event.setSeverity("P1");
        event.setTriggeredAt(LocalDateTime.now());
        return alertRepository.save(event);
    }
}