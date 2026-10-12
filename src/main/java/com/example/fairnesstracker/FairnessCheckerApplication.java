package com.example.fairnesstracker;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@RestController
public class FairnessCheckerApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(FairnessCheckerApplication.class, args);
        // ops/migrate-db.sh: start (migrate, check settings, create the first admin), then stop. Scheduled
        // jobs would otherwise keep the JVM running
        if (context.getEnvironment().getProperty("app.exit-after-startup", Boolean.class, false)) {
            System.exit(SpringApplication.exit(context));
        }
    }

    @Hidden
    @GetMapping("/")
    public String healthCheck() {
        return "Fairness Checker API Service is running!";
    }

}
