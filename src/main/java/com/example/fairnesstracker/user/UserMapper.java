package com.example.fairnesstracker.user;

import com.example.fairnesstracker.common.mapping.MappingConfig;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.dto.TeamDtos.TeamRef;
import com.example.fairnesstracker.user.dto.UserDtos.UserAccountResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mapper(config = MappingConfig.class)
public interface UserMapper {

    @Mapping(target = "engineerId", source = "engineer.id")
    @Mapping(target = "engineerName", source = "engineer.name")
    UserAccountResponse toResponse(AppUser user);

    default List<TeamRef> teams(Set<Team> teams) {
        return teams.stream()
                .sorted(Comparator.comparing(Team::getName))
                .map(team -> new TeamRef(team.getId(), team.getName()))
                .toList();
    }
}
