package com.example.fairnesstracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableScheduling
@RestController
public class FairnessCheckerApplication {

    public static void main(String[] args) {

        SpringApplication.run(FairnessCheckerApplication.class, args);
    }

}
