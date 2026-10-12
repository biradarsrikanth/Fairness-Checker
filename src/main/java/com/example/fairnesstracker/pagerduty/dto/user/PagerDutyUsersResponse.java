package com.example.fairnesstracker.pagerduty.dto.user;

import lombok.Data;
import java.util.List;

@Data
public class PagerDutyUsersResponse {

    private List<PagerDutyUser> users;

    private Integer limit;

    private Integer offset;

    private Boolean more;
}
