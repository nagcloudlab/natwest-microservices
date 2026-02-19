package com.ftgo.delivery;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Anti-Corruption Layer + Circuit Breaker for the Delivery Service.
 *
 * Iteration 9 change: RestTemplate is now injected as a @LoadBalanced bean.
 * URLs use Eureka service names (http://delivery-service/...) — no hardcoded host:port.
 */
@Slf4j
@Service("deliveryService")
public class DeliveryServiceClient {

    private final RestTemplate restTemplate;

    public DeliveryServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        log.info("DeliveryServiceClient initialized with @LoadBalanced RestTemplate (Eureka discovery)");
    }

    @CircuitBreaker(name = "deliveryService", fallbackMethod = "createDeliveryFallback")
    public DeliveryResponse createDelivery(Long orderId, String pickupAddress, String deliveryAddress) {
        log.info(">>> Calling delivery-service to create delivery for order #{}", orderId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "orderId", orderId,
                "pickupAddress", pickupAddress,
                "deliveryAddress", deliveryAddress
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(
                "http://delivery-service/api/deliveries",
                request,
                DeliveryResponse.class);
    }

    @CircuitBreaker(name = "deliveryService", fallbackMethod = "getAllDeliveriesFallback")
    public List<DeliveryResponse> getAllDeliveries() {
        log.debug("Fetching all deliveries from delivery-service");
        return restTemplate.exchange(
                "http://delivery-service/api/deliveries",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<DeliveryResponse>>() {}
        ).getBody();
    }

    @CircuitBreaker(name = "deliveryService", fallbackMethod = "getDeliveriesByCourierIdFallback")
    public List<DeliveryResponse> getDeliveriesByCourierId(Long courierId) {
        log.debug("Fetching deliveries for courier #{} from delivery-service", courierId);
        return restTemplate.exchange(
                "http://delivery-service/api/deliveries/courier/{courierId}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<DeliveryResponse>>() {},
                courierId
        ).getBody();
    }

    @CircuitBreaker(name = "deliveryService", fallbackMethod = "getAllCouriersFallback")
    public List<DeliveryResponse> getAllCouriers() {
        log.debug("Fetching all couriers from delivery-service");
        return restTemplate.exchange(
                "http://delivery-service/api/deliveries/couriers",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<DeliveryResponse>>() {}
        ).getBody();
    }

    @CircuitBreaker(name = "deliveryService", fallbackMethod = "getCourierFallback")
    public DeliveryResponse getCourier(Long id) {
        log.debug("Fetching courier #{} from delivery-service", id);
        return restTemplate.getForObject(
                "http://delivery-service/api/deliveries/couriers/{id}",
                DeliveryResponse.class,
                id);
    }

    @CircuitBreaker(name = "deliveryService", fallbackMethod = "assignSpecificCourierFallback")
    public DeliveryResponse assignSpecificCourier(Long deliveryId, Long courierId) {
        log.info(">>> Calling delivery-service to assign courier #{} to delivery #{}", courierId, deliveryId);
        return restTemplate.exchange(
                "http://delivery-service/api/deliveries/{id}/assign/{courierId}",
                HttpMethod.PUT,
                null,
                DeliveryResponse.class,
                deliveryId,
                courierId
        ).getBody();
    }

    @CircuitBreaker(name = "deliveryService", fallbackMethod = "assignCourierFallback")
    public DeliveryResponse assignCourier(Long deliveryId) {
        log.info(">>> Calling delivery-service to auto-assign courier to delivery #{}", deliveryId);
        return restTemplate.exchange(
                "http://delivery-service/api/deliveries/{id}/assign",
                HttpMethod.PUT,
                null,
                DeliveryResponse.class,
                deliveryId
        ).getBody();
    }

    @CircuitBreaker(name = "deliveryService", fallbackMethod = "markPickedUpFallback")
    public DeliveryResponse markPickedUp(Long deliveryId) {
        log.info(">>> Calling delivery-service to mark delivery #{} as picked up", deliveryId);
        return restTemplate.exchange(
                "http://delivery-service/api/deliveries/{id}/pickup",
                HttpMethod.PUT,
                null,
                DeliveryResponse.class,
                deliveryId
        ).getBody();
    }

    @CircuitBreaker(name = "deliveryService", fallbackMethod = "markDeliveredFallback")
    public DeliveryResponse markDelivered(Long deliveryId) {
        log.info(">>> Calling delivery-service to mark delivery #{} as delivered", deliveryId);
        return restTemplate.exchange(
                "http://delivery-service/api/deliveries/{id}/deliver",
                HttpMethod.PUT,
                null,
                DeliveryResponse.class,
                deliveryId
        ).getBody();
    }

    public boolean isReadyForPickup(Long orderId) {
        try {
            DeliveryResponse delivery = restTemplate.getForObject(
                    "http://delivery-service/api/deliveries/order/{orderId}",
                    DeliveryResponse.class,
                    orderId);
            return delivery != null && "COURIER_ASSIGNED".equals(delivery.getStatus());
        } catch (Exception e) {
            log.warn(">>> Could not check pickup readiness for order #{}: {}", orderId, e.getMessage());
            return false;
        }
    }

    // ======================== FALLBACK METHODS ========================

    public DeliveryResponse createDeliveryFallback(Long orderId, String pickupAddress, String deliveryAddress, Throwable t) {
        log.error(">>> CIRCUIT BREAKER: Delivery service unavailable for order #{}. Reason: {}", orderId, t.getMessage());
        throw new RuntimeException("Delivery service is currently unavailable. Please try again later. (Circuit Breaker active)");
    }

    public List<DeliveryResponse> getAllDeliveriesFallback(Throwable t) {
        log.warn(">>> FALLBACK: Delivery service unavailable, returning empty deliveries list. Reason: {}", t.getMessage());
        return List.of();
    }

    public List<DeliveryResponse> getDeliveriesByCourierIdFallback(Long courierId, Throwable t) {
        log.warn(">>> FALLBACK: Delivery service unavailable, returning empty deliveries list for courier #{}. Reason: {}", courierId, t.getMessage());
        return List.of();
    }

    public List<DeliveryResponse> getAllCouriersFallback(Throwable t) {
        log.warn(">>> FALLBACK: Delivery service unavailable, returning empty couriers list. Reason: {}", t.getMessage());
        return List.of();
    }

    public DeliveryResponse getCourierFallback(Long id, Throwable t) {
        log.warn(">>> FALLBACK: Delivery service unavailable for courier #{}. Reason: {}", id, t.getMessage());
        return null;
    }

    public DeliveryResponse assignSpecificCourierFallback(Long deliveryId, Long courierId, Throwable t) {
        log.error(">>> CIRCUIT BREAKER: Cannot assign courier #{} to delivery #{}. Reason: {}", courierId, deliveryId, t.getMessage());
        throw new RuntimeException("Delivery service is currently unavailable. Cannot assign courier.");
    }

    public DeliveryResponse assignCourierFallback(Long deliveryId, Throwable t) {
        log.error(">>> CIRCUIT BREAKER: Cannot auto-assign courier to delivery #{}. Reason: {}", deliveryId, t.getMessage());
        throw new RuntimeException("Delivery service is currently unavailable. Cannot assign courier.");
    }

    public DeliveryResponse markPickedUpFallback(Long deliveryId, Throwable t) {
        log.error(">>> CIRCUIT BREAKER: Cannot mark delivery #{} as picked up. Reason: {}", deliveryId, t.getMessage());
        throw new RuntimeException("Delivery service is currently unavailable. Cannot mark as picked up.");
    }

    public DeliveryResponse markDeliveredFallback(Long deliveryId, Throwable t) {
        log.error(">>> CIRCUIT BREAKER: Cannot mark delivery #{} as delivered. Reason: {}", deliveryId, t.getMessage());
        throw new RuntimeException("Delivery service is currently unavailable. Cannot mark as delivered.");
    }
}
