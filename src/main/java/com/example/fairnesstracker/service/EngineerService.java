package com.example.fairnesstracker.service;


import com.example.fairnesstracker.dto.engineer.EngineerDtos.EngineerRequest;
import com.example.fairnesstracker.dto.engineer.EngineerDtos.EngineerResponse;
import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.exceptions.ConflictException;
import com.example.fairnesstracker.exceptions.ResourceNotFoundException;
import com.example.fairnesstracker.repository.AlertRepository;
import com.example.fairnesstracker.repository.EngineerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        engineer.setName(request.name());
        engineer.setEmail(request.email());
        engineer.setTeam(teamService.findByName(request.team()));
        engineer.setPagerDutyUserId(request.pagerDutyUserId());
        engineer.setActive(request.active() == null || request.active());
    }

    private Engineer find(Long id) {
        return engineerRepository.findById(id)
                .orElseThrow(()->
                        new ResourceNotFoundException(
                                "Engineer not found with id: " + id)
                );
    }
}
