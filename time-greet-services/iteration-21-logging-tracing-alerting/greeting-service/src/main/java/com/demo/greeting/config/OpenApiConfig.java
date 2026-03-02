package com.demo.greeting.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI greetingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Greeting Service API")
                        .description("Demonstrates structured logging, distributed tracing and Prometheus alerting")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("NatWest Training")
                                .url("https://github.com/natwest-microservices")));
    }
}
