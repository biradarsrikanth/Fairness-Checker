package com.example.fairnesstracker.engineer;

import com.example.fairnesstracker.common.mapping.MappingConfig;
import com.example.fairnesstracker.engineer.dto.EngineerDtos.EngineerResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MappingConfig.class)
public interface EngineerMapper {

    @Mapping(target = "team", source = "team.name")
    EngineerResponse toResponse(Engineer engineer);
}
