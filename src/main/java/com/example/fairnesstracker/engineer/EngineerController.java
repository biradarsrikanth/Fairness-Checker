package com.example.fairnesstracker.engineer;

import com.example.fairnesstracker.common.web.PageResponse;
import com.example.fairnesstracker.engineer.dto.EngineerDtos.EngineerRequest;
import com.example.fairnesstracker.engineer.dto.EngineerDtos.EngineerResponse;
import com.example.fairnesstracker.engineer.dto.EngineerFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



@Tag(name = "Engineers", description = "Engineers who go on call")
@RestController
@RequestMapping("/api/v1/engineers")
public class EngineerController {

    private final EngineerService engineerService;

    public EngineerController(EngineerService engineerService) {
        this.engineerService = engineerService;
    }

    @PostMapping
    public ResponseEntity<EngineerResponse> saveEngineer(@Valid @RequestBody EngineerRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(engineerService.saveEngineer(request));
    }

    @Operation(summary = "List engineers", description = "Sort by name (default) or email.")
    @GetMapping
    public PageResponse<EngineerResponse> list(@Valid @ParameterObject EngineerFilter filter,
                                               @ParameterObject @SortDefault("name") Pageable pageable) {
        return engineerService.list(filter, pageable);
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
