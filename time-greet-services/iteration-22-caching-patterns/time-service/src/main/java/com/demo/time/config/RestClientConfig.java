package com.demo.time.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient greetingRestClient(
            RestClient.Builder builder,
            @Value("${greeting.service.url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
