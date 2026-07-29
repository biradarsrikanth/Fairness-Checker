package com.example.fairnesstracker.service;

import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.repository.AlertRepository;
import com.example.fairnesstracker.repository.EngineerRepository;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final AlertRepository alertEventRepository;
    private final EngineerRepository engineerRepository;

    public void importCsv(MultipartFile file) throws Exception {

        CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream())
        );

        String[] row;
        reader.readNext(); // Skip Header

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("d/M/yyyy HH:mm");

        while ((row = reader.readNext()) != null) {

            AlertEvent event = new AlertEvent();

            event.setPagerDutyIncidentId(row[0]);
            event.setIncidentNumber(Integer.valueOf(row[1]));
            event.setTitle(row[2]);
            event.setStatus(row[3]);

            event.setTriggeredAt(
                    LocalDateTime.parse(row[4], formatter)
            );

            event.setResolvedAt(
                    LocalDateTime.parse(row[5], formatter)
            );

            event.setSeverity(row[6]);
            event.setServiceName(row[7]);
            event.setPagerDutyUserId(row[8]);
            event.setAssignedEngineerName(row[9]);
            event.setSource(row[10]);

            Optional<Engineer> engineer =
                    engineerRepository.findByName(
                            row[9]
                    );

            engineer.ifPresent(event::setEngineer);

            alertEventRepository.save(event);
        }
    }
}