package com.example.fairnesstracker.dto.pagerDuty.incident;

import lombok.Data;

@Data
public class LastStatusChangeBy {
    private String id;
    private String summary;
    // "user_reference" for a person; services and integrations can also change status
    private String type;
}
