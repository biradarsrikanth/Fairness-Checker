package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.service.EngineerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/engineers")
public class EngineerController {

    private final EngineerService engineerService;

    @Autowired
    public EngineerController(EngineerService engineerService) {
        this.engineerService = engineerService;
    }

    @PostMapping
    public ResponseEntity<Engineer> saveEngineer(@Valid @RequestBody Engineer engineer){
        Engineer newEngineer=engineerService.saveEngineer(engineer);
        return ResponseEntity.ok(engineer);
    }

    @GetMapping
    public List<Engineer> getAllEngineers(){
        return engineerService.getAllEngineers();
    }

    @GetMapping("/{id}")
    public Engineer getById(@PathVariable Long id){
        return engineerService.getById(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateEngineer(@PathVariable Long id,@Valid @RequestBody Engineer engineer){
        Engineer updateDetails=engineerService.updateEngineer(id,engineer);
        return ResponseEntity.ok("Engineer Details Updated!");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteEngineer(@PathVariable Long id) {
        engineerService.deleteEngineer(id);
        return ResponseEntity.ok("Engineer deleted successfully!");
    }
}
