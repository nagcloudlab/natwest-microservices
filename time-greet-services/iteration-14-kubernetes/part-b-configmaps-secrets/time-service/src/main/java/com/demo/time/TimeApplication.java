package com.demo.time;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class TimeApplication {
    public static void main(String[] args) {
        SpringApplication.run(TimeApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        // Disable HTTP keep-alive so each request opens a new TCP connection.
        // This lets K8s Service load-balance across pods per request.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("Connection", "close");
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}
