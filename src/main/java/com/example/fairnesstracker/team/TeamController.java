package com.example.fairnesstracker.team;

import com.example.fairnesstracker.security.AccessPolicy;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.fairnesstracker.team.dto.TeamDtos.TeamRequest;
import com.example.fairnesstracker.team.dto.TeamDtos.TeamResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Teams", description = "Teams and their timezones")
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    private final AccessPolicy access;

    public TeamController(TeamService teamService, AccessPolicy access) {
        this.teamService = teamService;
        this.access = access;
    }

    @GetMapping
    public List<TeamResponse> getAll() {
        return teamService.getAll();
    }

    @GetMapping("/{id}")
    public TeamResponse getById(@PathVariable Long id) {
        return teamService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody TeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public TeamResponse update(@PathVariable Long id, @Valid @RequestBody TeamRequest request) {
        access.requireManagedTeam(id);
        return teamService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        teamService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
