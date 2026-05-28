package com.example.fairnesstracker.service;


import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.repository.EngineerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EngineerService {
    private final EngineerRepository engineerRepository;

    @Autowired
    public EngineerService(EngineerRepository engineerRepository) {
        this.engineerRepository = engineerRepository;
    }

    //save Engineer
    public Engineer saveEngineer(Engineer engineer){
        return engineerRepository.save(engineer);
    }

    //List all Engineers
    public List<Engineer> getAllEngineers(){
        return engineerRepository.findAll();
    };

    //get ine Engineer by Id
    public Optional<Engineer> getById(Long id){
        return engineerRepository.findById(id);
    }

    //update Engineer
    public Engineer updateEngineer(Long id, Engineer updatedEngineer){
        Optional<Engineer> exisitingEngineer=engineerRepository.findById(id);

        Engineer engineer=exisitingEngineer.get();
        engineer.setName(updatedEngineer.getName());
        engineer.setEmail(updatedEngineer.getEmail());
        engineer.setTeam(updatedEngineer.getTeam());
        return engineerRepository.save(engineer);
    }

    //delete Engineer
    public void deleteEngineer(Long id){
        engineerRepository.deleteById(id);
    }

}
