package com.example.fairnesstracker.engineer;

import com.example.fairnesstracker.alert.AlertRepository;
import com.example.fairnesstracker.common.exception.ConflictException;
import com.example.fairnesstracker.common.exception.ResourceNotFoundException;
import com.example.fairnesstracker.engineer.dto.EngineerDtos.EngineerRequest;
import com.example.fairnesstracker.engineer.dto.EngineerDtos.EngineerResponse;
import com.example.fairnesstracker.team.TeamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class EngineerService {
    private final EngineerRepository engineerRepository;
    private final AlertRepository alertRepository;
    private final TeamService teamService;

    public EngineerService(EngineerRepository engineerRepository, AlertRepository alertRepository,
                           TeamService teamService) {
        this.engineerRepository = engineerRepository;
        this.alertRepository = alertRepository;
        this.teamService = teamService;
    }

    //save Engineer
    public EngineerResponse saveEngineer(EngineerRequest request){
        Engineer engineer = new Engineer();
        apply(engineer, request);
        return EngineerResponse.from(engineerRepository.save(engineer));
    }

    //List all Engineers
    @Transactional(readOnly = true)
    public List<EngineerResponse> getAllEngineers(){
        return engineerRepository.findAll().stream().map(EngineerResponse::from).toList();
    }


    //get one Engineer by id
    @Transactional(readOnly = true)
    public EngineerResponse getById(Long id){
        return EngineerResponse.from(find(id));
    }

    //update Engineer
    public EngineerResponse updateEngineer(Long id, EngineerRequest request){
        Engineer existingEngineer = find(id);
        apply(existingEngineer, request);
        return EngineerResponse.from(existingEngineer);
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
