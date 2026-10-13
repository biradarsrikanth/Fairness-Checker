package com.example.fairnesstracker.pagerduty;

import com.example.fairnesstracker.common.mapping.MappingConfig;
import com.example.fairnesstracker.pagerduty.dto.SyncActivityDtos.SyncRunResponse;
import com.example.fairnesstracker.pagerduty.dto.SyncActivityDtos.WebhookEventDetail;
import com.example.fairnesstracker.pagerduty.dto.SyncActivityDtos.WebhookEventResponse;
import com.example.fairnesstracker.pagerduty.sync.SyncRun;
import com.example.fairnesstracker.pagerduty.webhook.WebhookEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Duration;

@Mapper(config = MappingConfig.class)
public interface SyncActivityMapper {

    @Mapping(target = "durationMs", expression = "java(durationMs(run))")
    SyncRunResponse toResponse(SyncRun run);

    WebhookEventResponse toResponse(WebhookEvent event);

    WebhookEventDetail toDetail(WebhookEvent event);

    default Long durationMs(SyncRun run) {
        return run.getFinishedAt() != null
                ? Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis() : null;
    }
}
