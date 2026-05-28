package com.example.fairnesstracker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Entity
@Table(name = "alret_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Validated
public class AlertEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Id cannot be Empty!")
    private String EngineerId;

    @NotBlank(message = "Name cannot be Empty!")
    private String EngineerName;

    @NotNull(message = "Requires trigger Time")
    private LocalDateTime TriggeredAt;
    private LocalDateTime ResolvedAt;

    @Pattern(regexp = "P[123]", message = "Severity must be P1, P2, or P3")
    private String Severity;
}
