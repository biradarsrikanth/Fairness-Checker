package com.example.fairnesstracker.alert;

import com.example.fairnesstracker.alert.dto.AlertResponse;
import com.example.fairnesstracker.common.mapping.MappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MappingConfig.class)
public interface AlertMapper {

    @Mapping(target = "engineerId", source = "engineer.id")
    @Mapping(target = "engineerName", source = "engineer.name")
    @Mapping(target = "team", source = "engineer.team.name")
    @Mapping(target = "serviceName", source = "service.name")
    AlertResponse toResponse(AlertEvent alert);

    @Mapping(target = "engineerId", source = "engineer.id")
    @Mapping(target = "engineerName", source = "engineer.name")
    @Mapping(target = "pagerDutyUserId", source = "pagerdutyUserId")
    AlertResponse.Assignment toResponse(AlertAssignment assignment);
}
