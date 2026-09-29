package com.example.fairnesstracker.service;

import com.example.fairnesstracker.dto.team.TeamDtos.TeamRequest;
import com.example.fairnesstracker.dto.team.TeamDtos.TeamResponse;
import com.example.fairnesstracker.entity.Team;
import com.example.fairnesstracker.exceptions.BadRequestException;
import com.example.fairnesstracker.exceptions.ConflictException;
import com.example.fairnesstracker.exceptions.ResourceNotFoundException;
import com.example.fairnesstracker.repository.EngineerRepository;
import com.example.fairnesstracker.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional
public class TeamService {

    private final TeamRepository teamRepository;
    private final EngineerRepository engineerRepository;

    public TeamService(TeamRepository teamRepository, EngineerRepository engineerRepository) {
        this.teamRepository = teamRepository;
        this.engineerRepository = engineerRepository;
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> getAll() {
        return teamRepository.findAll().stream().map(TeamResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse getById(Long id) {
        return TeamResponse.from(find(id));
    }

    public TeamResponse create(TeamRequest request) {
        if (teamRepository.findByName(request.name()).isPresent()) {
            throw new ConflictException("Team '" + request.name() + "' already exists");
        }
        return TeamResponse.from(teamRepository.save(new Team(request.name(), timezone(request.timezone()))));
    }

    public TeamResponse update(Long id, TeamRequest request) {
        Team team = find(id);
        teamRepository.findByName(request.name())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("Team '" + request.name() + "' already exists");
                });
        team.setName(request.name());
        team.setTimezone(timezone(request.timezone()));
        return TeamResponse.from(team);
    }

    public void delete(Long id) {
        Team team = find(id);
        if (engineerRepository.existsByTeam_Id(id)) {
            throw new ConflictException("Team '" + team.getName() + "' still has engineers; move them first");
        }
        teamRepository.delete(team);
    }

    /** Finds a team by name for engineer requests; unknown names are a client error, not a new team. */
    @Transactional(readOnly = true)
    public Team findByName(String name) {
        return teamRepository.findByName(name)
                .orElseThrow(() -> new BadRequestException("Unknown team '" + name + "'; create it via /api/teams"));
    }

    private Team find(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + id));
    }

    private static String timezone(String value) {
        if (value == null || value.isBlank()) return Team.DEFAULT_TIMEZONE;
        try {
            return ZoneId.of(value).getId();
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown timezone '" + value + "'; use an IANA name like Asia/Kolkata");
        }
    }
}
