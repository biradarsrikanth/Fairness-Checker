package com.example.fairnesstracker.team;

import com.example.fairnesstracker.common.exception.ConflictException;
import com.example.fairnesstracker.common.exception.ResourceNotFoundException;
import com.example.fairnesstracker.common.exception.ValidationException;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.team.dto.TeamDtos.TeamRequest;
import com.example.fairnesstracker.team.dto.TeamDtos.TeamResponse;
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
            throw duplicateName(request.name());
        }
        return TeamResponse.from(teamRepository.save(new Team(request.name(), timezone(request.timezone()))));
    }

    public TeamResponse update(Long id, TeamRequest request) {
        Team team = find(id);
        teamRepository.findByName(request.name())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw duplicateName(request.name());
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
                .orElseThrow(() -> ValidationException.onField("team",
                        "Unknown team '" + name + "'; create it via /api/v1/teams"));
    }

    private Team find(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Team", id));
    }

    private static ConflictException duplicateName(String name) {
        return ConflictException.onField("name", "Team '" + name + "' already exists");
    }

    private static String timezone(String value) {
        if (value == null || value.isBlank()) return Team.DEFAULT_TIMEZONE;
        try {
            return ZoneId.of(value).getId();
        } catch (DateTimeException e) {
            throw ValidationException.onField("timezone",
                    "Unknown timezone '" + value + "'; use an IANA name like Asia/Kolkata");
        }
    }
}
