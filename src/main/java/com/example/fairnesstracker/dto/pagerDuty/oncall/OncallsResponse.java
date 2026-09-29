package com.example.fairnesstracker.dto.pagerDuty.oncall;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

// GET /oncalls
@Data
public class OncallsResponse {

    private List<Oncall> oncalls;
    private boolean more;

    @Data
    public static class Oncall {
        private Ref user;
        private Ref schedule;

        @JsonProperty("escalation_policy")
        private Ref escalationPolicy;

        @JsonProperty("escalation_level")
        private Integer escalationLevel;

        // Null for permanent (always on-call) entries
        private String start;
        private String end;
    }

    @Data
    public static class Ref {
        private String id;
        private String summary;
    }
}
