package com.enterprise.tacticalsim.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${ai.engine.url:http://localhost:8000}")
    private String aiEngineUrl;

    @Bean
    public WebClient aiEngineWebClient() {
        return WebClient.builder()
                .baseUrl(aiEngineUrl)
                .build();
    }
}