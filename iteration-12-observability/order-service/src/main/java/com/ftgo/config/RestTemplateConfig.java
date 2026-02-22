package com.ftgo.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Centralized RestTemplate bean with @LoadBalanced.
 *
 * @LoadBalanced tells Spring to intercept every RestTemplate call and resolve
 * the hostname (e.g., "http://restaurant-service/api/...") via the Eureka
 * registry instead of DNS. This is CLIENT-SIDE load balancing — the caller
 * picks the target instance, not a central proxy.
 *
 * The TokenPropagationInterceptor forwards the incoming JWT Authorization
 * header to downstream service calls, enabling token propagation across
 * the microservice mesh.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(TokenPropagationInterceptor tokenInterceptor) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(tokenInterceptor);
        return restTemplate;
    }
}
