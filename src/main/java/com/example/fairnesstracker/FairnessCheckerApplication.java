package com.example.fairnesstracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class FairnessCheckerApplication {

    public static void main(String[] args) {

        SpringApplication.run(FairnessCheckerApplication.class, args);
    }

    @GetMapping
    public String HealthController(){
        return "Let's Start!";
    }

}
