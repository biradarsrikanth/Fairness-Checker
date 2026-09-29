package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.dto.engineer.EngineerDtos.EngineerRequest;
import com.example.fairnesstracker.dto.engineer.EngineerDtos.EngineerResponse;
import com.example.fairnesstracker.service.EngineerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/engineers")
public class EngineerController {

    private final EngineerService engineerService;

    public EngineerController(EngineerService engineerService) {
        this.engineerService = engineerService;
    }

    @PostMapping
    public ResponseEntity<EngineerResponse> saveEngineer(@Valid @RequestBody EngineerRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(engineerService.saveEngineer(request));
    }

    @GetMapping
    public List<EngineerResponse> getAllEngineers(){
        return engineerService.getAllEngineers();
    }


    @GetMapping("/{id}")
    public EngineerResponse getById(@PathVariable Long id){
        return engineerService.getById(id);
    }

    @PutMapping("/{id}")
    public EngineerResponse updateEngineer(@PathVariable Long id, @Valid @RequestBody EngineerRequest request){
        return engineerService.updateEngineer(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEngineer(@PathVariable Long id) {
        engineerService.deleteEngineer(id);
        return ResponseEntity.noContent().build();
    }
}
