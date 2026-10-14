package com.example.fairnesstracker.pagerduty;

import com.example.fairnesstracker.common.exception.ApiException;
import com.example.fairnesstracker.common.exception.ErrorCode;

/** A PagerDuty call or sync was asked for on a server without a PagerDuty API token. */
public class PagerDutyNotConfiguredException extends ApiException {

    public PagerDutyNotConfiguredException() {
        super(ErrorCode.PAGERDUTY_NOT_CONFIGURED,
                "PagerDuty isn't set up on this server: set PAGERDUTY_API_TOKEN and restart the gateway");
    }
}
