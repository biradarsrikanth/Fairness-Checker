package com.example.fairnesstracker.team;

import com.example.fairnesstracker.common.mapping.MappingConfig;
import com.example.fairnesstracker.team.dto.TeamDtos.TeamResponse;
import org.mapstruct.Mapper;

@Mapper(config = MappingConfig.class)
public interface TeamMapper {

    TeamResponse toResponse(Team team);
}
