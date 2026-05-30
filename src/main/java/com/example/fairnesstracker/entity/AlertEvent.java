package com.example.fairnesstracker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
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
    @Setter(AccessLevel.NONE)
    private Long id;

    @NotBlank(message = "Id cannot be Empty!")
    private String engineerId;

    @NotBlank(message = "Name cannot be Empty!")
    private String engineerName;

    @NotNull(message = "Requires trigger Time")
    private LocalDateTime triggeredAt;
    private LocalDateTime resolvedAt;

    @Pattern(regexp = "P1|P2|P3", message = "Severity must be P1, P2, or P3")
    private String severity;
}
