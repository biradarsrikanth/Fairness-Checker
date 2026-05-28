package com.example.fairnesstracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "alret_event")

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="EngineerId",nullable = false)
    private String EngineerId;
    private String EngineerName;
    private LocalDateTime TriggeredAt;
    private LocalDateTime ResolvedAt;
    private String Severity;
}
