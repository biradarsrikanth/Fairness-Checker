package com.example.fairnesstracker.service;


import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.exceptions.ResourceNotFoundException;
import com.example.fairnesstracker.repository.EngineerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
    }


    //get ine Engineer by id
    public Engineer getById(Long id){

        return engineerRepository.findById(id)
                .orElseThrow(()->
                        new RuntimeException(
                                "Enineer not Found with id:"+id
                        )
                );
    }

    //update Engineer
    public Engineer updateEngineer(Long id, Engineer updatedEngineer){
        Engineer existingEngineer=engineerRepository.findById(id)
                .orElseThrow(()->
                        new RuntimeException(
                                "Enineer not Found with id:"+id
                        )
                );
        existingEngineer.setName(updatedEngineer.getName());
        existingEngineer.setEmail(updatedEngineer.getEmail());
        existingEngineer.setTeam(updatedEngineer.getTeam());
        return engineerRepository.save(existingEngineer);
    }

    //delete Engineer
    public void deleteEngineer(Long id){

        Engineer engineer = engineerRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Engineer not found with id: " + id));

        engineerRepository.delete(engineer);
    }
}

