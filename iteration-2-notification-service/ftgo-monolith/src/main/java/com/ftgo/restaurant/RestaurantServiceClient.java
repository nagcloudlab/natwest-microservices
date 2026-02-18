package com.ftgo.restaurant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Strangler Fig in action!
 *
 * This class replaces the old RestaurantService that talked directly to the database.
 * It exposes the SAME method signatures, so ConsumerWebController, RestaurantWebController,
 * and OrderService don't need ANY changes — they still call getRestaurant(), getAllRestaurants(), etc.
 *
 * Under the hood, every call now goes over HTTP to the Restaurant Service on port 8081.
 *
 * Key teaching points:
 * 1. The proxy is TRANSPARENT — callers don't know the data comes from another service
 * 2. This introduces a new failure mode — what if restaurant-service is down?
 *    (We'll add Circuit Breaker in iteration 3 to handle this)
 * 3. No createRestaurant() here — write operations go directly to restaurant-service's API
 */
@Service("restaurantService")
public class RestaurantServiceClient {

    private final RestTemplate restTemplate;
    private final String restaurantServiceUrl;

    public RestaurantServiceClient(
            @Value("${restaurant-service.url:http://localhost:8081}") String restaurantServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.restaurantServiceUrl = restaurantServiceUrl;
    }

    public Restaurant getRestaurant(Long id) {
        return restTemplate.getForObject(
                restaurantServiceUrl + "/api/restaurants/{id}",
                Restaurant.class,
                id);
    }

    public List<MenuItem> getMenuItemsByIds(List<Long> ids) {
        String idsParam = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return restTemplate.exchange(
                restaurantServiceUrl + "/api/restaurants/menu-items?ids={ids}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<MenuItem>>() {},
                idsParam
        ).getBody();
    }

    public List<Restaurant> getAllRestaurants() {
        return restTemplate.exchange(
                restaurantServiceUrl + "/api/restaurants",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Restaurant>>() {}
        ).getBody();
    }
}
