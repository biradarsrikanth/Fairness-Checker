package com.example.fairnesstracker.engineer;

import com.example.fairnesstracker.security.AccessPolicy;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final AccessPolicy access;

    public EngineerController(EngineerService engineerService, AccessPolicy access) {
        this.engineerService = engineerService;
        this.access = access;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EngineerResponse> saveEngineer(@Valid @RequestBody EngineerRequest request){
        access.requireManagedTeamNamed(request.team());
        return ResponseEntity.status(HttpStatus.CREATED).body(engineerService.saveEngineer(request));
    }

    @Operation(summary = "List engineers", description = "Sort by name (default) or email.")
    @GetMapping
    public PageResponse<EngineerResponse> list(@Valid @ParameterObject EngineerFilter filter,
                                               @ParameterObject @SortDefault("name") Pageable pageable) {
        return engineerService.list(filter, pageable, access.scope());
    }


    @GetMapping("/{id}")
    public EngineerResponse getById(@PathVariable Long id){
        access.requireEngineer(id);
        return engineerService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public EngineerResponse updateEngineer(@PathVariable Long id, @Valid @RequestBody EngineerRequest request){
        // Both the engineer's current team and the one they move to must be the manager's
        access.requireManagedEngineer(id);
        access.requireManagedTeamNamed(request.team());
        return engineerService.updateEngineer(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteEngineer(@PathVariable Long id) {
        access.requireManagedEngineer(id);
        engineerService.deleteEngineer(id);
        return ResponseEntity.noContent().build();
    }
}
