package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.dto.AlertEventImportDto;
import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.repository.AlertRepository;
import com.example.fairnesstracker.repository.EngineerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final AlertRepository alertEventRepository;
    private final EngineerRepository engineerRepository;

    @PostMapping
    public ResponseEntity<String> importAlerts(
            @RequestBody List<AlertEventImportDto> alerts) {
        try {
            DateTimeFormatter formatter =
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME;

            Integer nextIncidentNumber =
                    alertEventRepository.findMaxIncidentNumber() + 1;

            for (AlertEventImportDto dto : alerts) {

                AlertEvent event = new AlertEvent();

                event.setPagerDutyIncidentId(dto.getPagerDutyIncidentId());
                event.setIncidentNumber(nextIncidentNumber++);
                event.setTitle(dto.getTitle());
                event.setStatus(dto.getStatus());

                if (dto.getTriggeredAt() == null) {
                    throw new RuntimeException(
                            "triggeredAt is null for " + dto.getPagerDutyIncidentId());
                }

                event.setTriggeredAt(
                        LocalDateTime.parse(dto.getTriggeredAt(), formatter)
                );

                if (dto.getResolvedAt() != null &&
                        !dto.getResolvedAt().isBlank()) {

                    event.setResolvedAt(
                            LocalDateTime.parse(dto.getResolvedAt(), formatter)
                    );
                }

                event.setSeverity(dto.getSeverity());
                event.setServiceId(dto.getServiceId());
                event.setUrgency(dto.getUrgency());
                event.setServiceName(dto.getServiceName());
                event.setPagerDutyUserId(dto.getPagerDutyUserId());
                event.setAssignedEngineerName(dto.getAssignedEngineerName());
                event.setSource(dto.getSource());


                engineerRepository
                        .findByName(dto.getAssignedEngineerName())
                        .ifPresent(event::setEngineer);

                alertEventRepository.save(event);
            }

            return ResponseEntity.ok(
                    "Imported " + alerts.size() + " alerts"
            );
        }catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(e.getClass().getName() + " : " + e.getMessage());
        }
    }
}
