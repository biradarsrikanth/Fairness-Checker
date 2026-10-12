package com.example.fairnesstracker.engineer;

import com.example.fairnesstracker.alert.AlertRepository;
import com.example.fairnesstracker.common.exception.ConflictException;
import com.example.fairnesstracker.common.exception.ResourceNotFoundException;
import com.example.fairnesstracker.common.web.PageResponse;
import com.example.fairnesstracker.common.web.Sorting;
import com.example.fairnesstracker.engineer.dto.EngineerDtos.EngineerRequest;
import com.example.fairnesstracker.engineer.dto.EngineerDtos.EngineerResponse;
import com.example.fairnesstracker.engineer.dto.EngineerFilter;
import com.example.fairnesstracker.team.TeamService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
public class EngineerService {

    static final Set<String> SORTABLE = Set.of("name", "email");

    private final EngineerRepository engineerRepository;
    private final AlertRepository alertRepository;
    private final TeamService teamService;
    private final EngineerMapper engineerMapper;

    public EngineerService(EngineerRepository engineerRepository, AlertRepository alertRepository,
                           TeamService teamService, EngineerMapper engineerMapper) {
        this.engineerRepository = engineerRepository;
        this.alertRepository = alertRepository;
        this.teamService = teamService;
        this.engineerMapper = engineerMapper;
    }

    //save Engineer
    public EngineerResponse saveEngineer(EngineerRequest request){
        Engineer engineer = new Engineer();
        apply(engineer, request);
        return engineerMapper.toResponse(engineerRepository.save(engineer));
    }

    /** One page of engineers, optionally of one team and/or only (in)active ones; by name unless sorted otherwise. */
    @Transactional(readOnly = true)
    public PageResponse<EngineerResponse> list(EngineerFilter filter, Pageable pageable) {
        List<Specification<Engineer>> conditions = new ArrayList<>();
        if (filter.team() != null && !filter.team().isBlank()) {
            conditions.add((engineer, query, cb) -> cb.equal(engineer.get("team").get("name"), filter.team().strip()));
        }
        if (filter.active() != null) {
            conditions.add((engineer, query, cb) -> cb.equal(engineer.get("active"), filter.active()));
        }
        Page<Engineer> page = engineerRepository.findAll(Specification.allOf(conditions),
                Sorting.restrict(pageable, SORTABLE));
        return PageResponse.from(page.map(engineerMapper::toResponse));
    }


    //get one Engineer by id
    @Transactional(readOnly = true)
    public EngineerResponse getById(Long id){
        return engineerMapper.toResponse(find(id));
    }

    //update Engineer
    public EngineerResponse updateEngineer(Long id, EngineerRequest request){
        Engineer existingEngineer = find(id);
        apply(existingEngineer, request);
        return engineerMapper.toResponse(existingEngineer);
    }

    // Engineers with history must be deactivated instead, or their alerts would lose their owner
    public void deleteEngineer(Long id){
        Engineer engineer = find(id);
        if (alertRepository.existsByEngineer_Id(id)) {
            throw new ConflictException("Engineer " + id + " has alerts; set \"active\": false instead of deleting");
        }
        engineerRepository.delete(engineer);
    }

    private void apply(Engineer engineer, EngineerRequest request) {
        // Blank means "not linked to PagerDuty"; stored as NULL so the unique key allows many of them
        String pagerDutyUserId = request.pagerDutyUserId() == null || request.pagerDutyUserId().isBlank()
                ? null : request.pagerDutyUserId().strip();
        rejectDuplicates(engineer.getId(), request.email(), pagerDutyUserId);
        engineer.setName(request.name());
        engineer.setEmail(request.email());
        engineer.setTeam(teamService.findByName(request.team()));
        engineer.setPagerDutyUserId(pagerDutyUserId);
        engineer.setActive(request.active() == null || request.active());
    }

    // Both are unique in the database (email regardless of case); checking first names the field
    private void rejectDuplicates(Long id, String email, String pagerDutyUserId) {
        engineerRepository.findByEmailIgnoreCase(email)
                .filter(other -> !Objects.equals(other.getId(), id))
                .ifPresent(other -> {
                    throw ConflictException.onField("email", "Engineer " + other.getId() + " already uses " + email);
                });
        if (pagerDutyUserId != null) {
            engineerRepository.findByPagerDutyUserId(pagerDutyUserId)
                    .filter(other -> !Objects.equals(other.getId(), id))
                    .ifPresent(other -> {
                        throw ConflictException.onField("pagerDutyUserId",
                                "Engineer " + other.getId() + " is already linked to PagerDuty user " + pagerDutyUserId);
                    });
        }
    }

    private Engineer find(Long id) {
        return engineerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Engineer", id));
    }
}
