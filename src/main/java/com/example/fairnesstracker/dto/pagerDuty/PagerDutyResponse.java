package com.example.fairnesstracker.dto.pagerDuty;

import lombok.Data;

import java.util.List;

@Data
public class PagerDutyResponse {
        private List<PagerDutyIncident> incidents;
        private Integer limit;
        private Integer offset;
        private boolean  more;
}
