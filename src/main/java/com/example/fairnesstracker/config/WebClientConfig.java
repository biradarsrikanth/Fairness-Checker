package com.example.fairnesstracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${pagerduty.base-url}")
    private String baseUrl;

    @Value("${pagerduty.api-token}")
    private String apiToken;

    @Bean
    public WebClient pagerDutyWebClient() {

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(
                        "Authorization",
                        "Token token=" + apiToken
                )
                .defaultHeader(
                        "Accept",
                        "application/vnd.pagerduty+json;version=2"
                )
                .defaultHeader(
                        "Content-Type",
                        "application/json"
                )
                .build();
    }
}