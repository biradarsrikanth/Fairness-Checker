package com.example.fairnesstracker.dto.alert;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;


public record AlertRequest(
        @NotNull
        Long engineerId,
        @Pattern(regexp = "P1|P2|P3",
                message = "Severity must be P1, P2 or P3"
        )
        String severity
) {}
