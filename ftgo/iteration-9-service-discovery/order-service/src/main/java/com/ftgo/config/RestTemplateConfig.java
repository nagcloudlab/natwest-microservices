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
 * Before (Iteration 8): Each service client created its own RestTemplate with
 * a hardcoded URL from @Value("${restaurant-service.url}"). Four clients =
 * four RestTemplates = four hardcoded URLs.
 *
 * After (Iteration 9): One shared @LoadBalanced RestTemplate bean. URLs use
 * Eureka service names (http://restaurant-service/...) with no port numbers.
 * Eureka resolves the service name to an actual host:port at runtime.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }
}
