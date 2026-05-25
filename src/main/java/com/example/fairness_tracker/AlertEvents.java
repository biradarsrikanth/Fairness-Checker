package com.example.fairness_tracker;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "alret_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvents {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="EngineerId",nullable = false)
    private String EngineerId;
    private String EngineerName;
    private LocalDateTime triggerdAt;
    private LocalDateTime resolvedAt;
    private String Severity;
}
