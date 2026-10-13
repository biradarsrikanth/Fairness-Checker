package com.example.fairnesstracker.alert.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;


public record AlertRequest(
        @NotNull
        Long engineerId,
        @Pattern(regexp = "P1|P2|P3|P4|P5", message = "must be one of P1, P2, P3, P4, P5")
        @Nullable String severity
) {}
