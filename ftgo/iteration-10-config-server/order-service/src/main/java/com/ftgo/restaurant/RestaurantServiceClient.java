package com.ftgo.restaurant;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Strangler Fig client for Restaurant Service — now with ALL fault tolerance patterns!
 *
 * This class demonstrates 5 of the 6 MicroProfile Fault Tolerance patterns:
 *
 * | # | Pattern          | Where Applied        | Why It Fits                                    |
 * |---|------------------|----------------------|------------------------------------------------|
 * | 1 | @Timeout         | RestTemplateConfig   | Prevent hanging on slow restaurant-service     |
 * | 2 | @Retry           | getRestaurant()      | Idempotent GET — retry transient failures      |
 * | 3 | @Fallback        | getAllRestaurants()   | Non-critical browsing — degrade gracefully     |
 * | 4 | @CircuitBreaker  | All methods          | Fail fast when restaurant-service is down      |
 * | 5 | @Bulkhead        | getAllRestaurants()   | High-traffic browsing — limit concurrency      |
 *
 * Iteration 9 change: RestTemplate is now injected as a @LoadBalanced bean.
 * URLs use Eureka service names (http://restaurant-service/...) — no hardcoded host:port.
 */
@Slf4j
@Service("restaurantService")
public class RestaurantServiceClient {

    private final RestTemplate restTemplate;

    public RestaurantServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        log.info("RestaurantServiceClient initialized with @LoadBalanced RestTemplate (Eureka discovery)");
    }

    @Retry(name = "restaurantService", fallbackMethod = "getRestaurantFallback")
    @CircuitBreaker(name = "restaurantService", fallbackMethod = "getRestaurantFallback")
    public Restaurant getRestaurant(Long id) {
        log.debug("Fetching restaurant #{} from restaurant-service", id);
        return restTemplate.getForObject(
                "http://restaurant-service/api/restaurants/{id}",
                Restaurant.class,
                id);
    }

    @Bulkhead(name = "restaurantService", fallbackMethod = "getAllRestaurantsFallback")
    @CircuitBreaker(name = "restaurantService", fallbackMethod = "getAllRestaurantsFallback")
    public List<Restaurant> getAllRestaurants() {
        log.debug("Fetching all restaurants from restaurant-service");
        return restTemplate.exchange(
                "http://restaurant-service/api/restaurants",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Restaurant>>() {}
        ).getBody();
    }

    @CircuitBreaker(name = "restaurantService", fallbackMethod = "getMenuItemsByIdsFallback")
    public List<MenuItem> getMenuItemsByIds(List<Long> ids) {
        log.debug("Fetching menu items {} from restaurant-service", ids);
        String idsParam = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return restTemplate.exchange(
                "http://restaurant-service/api/restaurants/menu-items?ids={ids}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<MenuItem>>() {},
                idsParam
        ).getBody();
    }

    // ======================== FALLBACK METHODS ========================

    public Restaurant getRestaurantFallback(Long id, Throwable t) {
        log.error(">>> FALLBACK: Cannot fetch restaurant #{}. Reason: {}", id, t.getMessage());
        throw new RuntimeException(
                "Restaurant service is currently unavailable. Please try again later.");
    }

    public List<Restaurant> getAllRestaurantsFallback(Throwable t) {
        log.warn(">>> FALLBACK: Restaurant service unavailable, returning empty list. Reason: {}", t.getMessage());
        return List.of();
    }

    public List<MenuItem> getMenuItemsByIdsFallback(List<Long> ids, Throwable t) {
        log.error(">>> FALLBACK: Cannot fetch menu items {}. Reason: {}", ids, t.getMessage());
        throw new RuntimeException(
                "Restaurant service is currently unavailable. Cannot retrieve menu items for order.");
    }
}
