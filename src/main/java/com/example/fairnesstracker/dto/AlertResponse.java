package com.example.fairnesstracker.dto;

import java.time.LocalDateTime;

public record AlertResponse(
        Long id,
        String engineerName,
        String severity,
        LocalDateTime triggeredAt,
        LocalDateTime resolvedAt
) {}
