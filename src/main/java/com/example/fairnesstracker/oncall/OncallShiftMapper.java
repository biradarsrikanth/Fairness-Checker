package com.example.fairnesstracker.oncall;

import com.example.fairnesstracker.common.mapping.MappingConfig;
import com.example.fairnesstracker.oncall.dto.OncallShiftResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MappingConfig.class)
public interface OncallShiftMapper {

    @Mapping(target = "engineerId", source = "engineer.id")
    @Mapping(target = "engineerName", source = "engineer.name")
    @Mapping(target = "team", source = "engineer.team.name")
    OncallShiftResponse toResponse(OncallShift shift);
}
