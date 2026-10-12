package com.example.fairnesstracker.pagerduty.dto.incident;

import lombok.Data;

import java.util.List;

@Data
public class PagerDutyResponse {
        private List<PagerDutyIncident> incidents;
        private Integer limit;
        private Integer offset;
        private boolean  more;
}
