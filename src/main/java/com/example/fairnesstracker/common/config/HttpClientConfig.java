package com.example.fairnesstracker.common.config;

import com.example.fairnesstracker.pagerduty.PagerDutyProperties;
import com.example.fairnesstracker.scoring.ScorerProperties;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

// Every outbound client has a connect and a response timeout, so a slow dependency can't hold
// request threads forever
@Configuration
public class HttpClientConfig {

    // PagerDuty pages of 100 incidents are larger than WebClient's 256 KB default buffer
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    @Bean("scorerWebClient")
    public WebClient scorerWebClient(WebClient.Builder builder,
                                     ScorerProperties props,
                                     JsonMapper.Builder jsonMapperBuilder) {
        // The scorer speaks snake_case; our own API stays camelCase. Keep the scorer's timezone offset
        // (e.g. +05:30) instead of converting window times to UTC, and ignore fields we don't know yet so
        // the scorer can add fields without breaking the gateway
        JsonMapper snakeCase = jsonMapperBuilder
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        return builder.clone()
                .clientConnector(connector(props.connectTimeout(), props.responseTimeout()))
                .baseUrl(props.baseUrl())
                .defaultHeader("X-API-Key", props.apiKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().jacksonJsonDecoder(
                        new JacksonJsonDecoder(snakeCase, MediaType.APPLICATION_JSON)))
                .build();
    }

    @Bean("pagerDutyClient")
    public WebClient pagerDutyClient(WebClient.Builder builder, PagerDutyProperties props) {
        return builder.clone()
                .clientConnector(connector(props.connectTimeout(), props.responseTimeout()))
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Token token=" + props.apiToken())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.pagerduty+json;version=2")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
    }

    private static ReactorClientHttpConnector connector(Duration connectTimeout, Duration responseTimeout) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()))
                .responseTimeout(responseTimeout);
        return new ReactorClientHttpConnector(http);
    }
}
