package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.service.EngineerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping
public class EngineerController {

    private final EngineerService engineerService;

    @Autowired
    public EngineerController(EngineerService engineerService) {
        this.engineerService = engineerService;
    }

    @PostMapping("/engineer")
    public ResponseEntity<Engineer> saveEngineer(@RequestBody Engineer engineer){
        Engineer newEngineer=engineerService.saveEngineer(engineer);
        return ResponseEntity.ok(engineer);
    }

    @GetMapping("/engineers")
    public List<Engineer> getAllEngineers(){
        return engineerService.getAllEngineers();
    }

    @GetMapping("/engineers/{id}")
    public ResponseEntity<Engineer> getById(@PathVariable Long id){
        Optional<Engineer> engineer=engineerService.getById(id);
        return engineer.map(ResponseEntity::ok).orElseGet(()-> ResponseEntity.notFound().build());
    }

    @PostMapping("/engineers/{id}")
    public ResponseEntity<String> updateEngineer(@PathVariable Long id,@RequestBody Engineer engineer){
        Engineer updateDetails=engineerService.updateEngineer(id,engineer);
        return ResponseEntity.ok("Engineer Details Updated!");
    }

    @DeleteMapping("/engineers/{id}")
    public ResponseEntity<String> deleteEngineer(@PathVariable Long id){
        engineerService.deleteEngineer(id);
        return ResponseEntity.ok("Engineer Data Deleted Succesfully!");
    }
}
