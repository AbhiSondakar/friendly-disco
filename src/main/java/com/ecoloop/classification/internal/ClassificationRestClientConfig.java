package com.ecoloop.classification.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ClassificationRestClientConfig {

    @Value("${ai.roboflow.api-key:}")
    private String roboflowApiKey;

    private SimpleClientHttpRequestFactory createRequestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    @Bean(name = "roboflowRestClient")
    RestClient roboflowRestClient() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://serverless.roboflow.com")
            .requestFactory(createRequestFactory())
            .defaultHeader("Content-Type", "application/json");
        if (roboflowApiKey != null && !roboflowApiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + roboflowApiKey);
        }
        return builder.build();
    }
}
