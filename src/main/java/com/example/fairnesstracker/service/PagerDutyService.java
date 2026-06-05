package com.example.fairnesstracker.service;

import com.example.fairnesstracker.dto.pagerDuty.PagerDutyResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;


@Service
public class PagerDutyService {

    private final WebClient webClient;
    private final String apiToken;

    @Autowired
    public PagerDutyService(WebClient webClient, @Value("${pagerduty.api-token}") String apiToken) {
        this.webClient = webClient;
        this.apiToken = apiToken;
    }

    public PagerDutyResponse getIncidents(){
       return webClient.get()
               .uri("/incidents")
               .header("Authorization", "Token token=" + apiToken)
               .header("Accept", "application/vnd.pagerduty+json;version=2")
               .retrieve()
               .bodyToMono(PagerDutyResponse.class)
               .block();
    }
}