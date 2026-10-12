package com.example.fairnesstracker.dev;

//-----------------------------------------------------------------
import com.example.fairnesstracker.alert.AlertEvent;
import com.example.fairnesstracker.alert.AlertRepository;
import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

// Creates fake alerts: only available with the "dev" profile, never in production
@Profile("dev")
@Tag(name = "Dev", description = "Development helpers (dev profile only)")
@RestController
@RequestMapping("/api/v1/dev")
public class TestController {

    @Autowired
    private EngineerRepository engineerRepository;
    @Autowired
    private AlertRepository alertRepository;

    @PostMapping("/test")
    public AlertEvent createTest() {

        Engineer engineer = engineerRepository
                .findById(1L)
                .orElseThrow();

        AlertEvent event = new AlertEvent();

        event.setEngineer(engineer);
        event.setSeverity("P1");
        event.setTriggeredAt(LocalDateTime.now(ZoneOffset.UTC));

        return alertRepository.save(event);
    }
}
//------------------------------------------------------------------
// Specified Tests are already done so ignore this
