package com.ftgo.delivery;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Anti-Corruption Layer + Circuit Breaker + Timeout for the Delivery Service.
 *
 * Replaces the old DeliveryService (which had @Autowired DeliveryRepository, CourierRepository).
 * The monolith no longer owns delivery or courier data — it calls delivery-service via REST.
 *
 * Fault tolerance patterns applied:
 *
 * | # | Pattern          | Where Applied          | Why It Fits                                    |
 * |---|------------------|------------------------|------------------------------------------------|
 * | 1 | @Timeout         | Constructor            | Prevent hanging on slow delivery-service       |
 * | 4 | @CircuitBreaker  | createDelivery()       | Fail fast when delivery-service is down        |
 * | 3 | @Fallback        | getAllDeliveries()      | Graceful degradation for UI browsing           |
 *
 * Why NO @Retry on createDelivery()?
 *   POST /api/deliveries is NOT idempotent — retrying could create DUPLICATE deliveries!
 */
@Slf4j
@Service("deliveryService")
public class DeliveryServiceClient {

    private final RestTemplate restTemplate;
    private final String deliveryServiceUrl;

    public DeliveryServiceClient(
            @Value("${delivery-service.url:http://localhost:8085}") String deliveryServiceUrl,
            @Value("${delivery-service.connect-timeout:2000}") int connectTimeout,
            @Value("${delivery-service.read-timeout:3000}") int readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restTemplate = new RestTemplate(factory);
        this.deliveryServiceUrl = deliveryServiceUrl;

        log.info("DeliveryServiceClient initialized: url={}, connectTimeout={}ms, readTimeout={}ms",
                deliveryServiceUrl, connectTimeout, readTimeout);
    }

    /**
     * Create a delivery. Called by OrderService during order creation.
     *
     * Pattern 4: @CircuitBreaker — fail fast if delivery-service is down.
     * No @Retry — POST is not idempotent.
     */
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
                deliveryServiceUrl + "/api/deliveries",
                request,
                DeliveryResponse.class);
    }

    /**
     * Get all deliveries. Called by CourierWebController for the UI.
     */
    @CircuitBreaker(name = "deliveryService", fallbackMethod = "getAllDeliveriesFallback")
    public List<DeliveryResponse> getAllDeliveries() {
        log.debug("Fetching all deliveries from delivery-service");
        return restTemplate.exchange(
                deliveryServiceUrl + "/api/deliveries",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<DeliveryResponse>>() {}
        ).getBody();
    }

    /**
     * Get deliveries for a specific courier.
     */
    @CircuitBreaker(name = "deliveryService", fallbackMethod = "getDeliveriesByCourierIdFallback")
    public List<DeliveryResponse> getDeliveriesByCourierId(Long courierId) {
        log.debug("Fetching deliveries for courier #{} from delivery-service", courierId);
        return restTemplate.exchange(
                deliveryServiceUrl + "/api/deliveries/courier/{courierId}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<DeliveryResponse>>() {},
                courierId
        ).getBody();
    }

    /**
     * Get all couriers.
     */
    @CircuitBreaker(name = "deliveryService", fallbackMethod = "getAllCouriersFallback")
    public List<DeliveryResponse> getAllCouriers() {
        log.debug("Fetching all couriers from delivery-service");
        return restTemplate.exchange(
                deliveryServiceUrl + "/api/deliveries/couriers",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<DeliveryResponse>>() {}
        ).getBody();
    }

    /**
     * Get a specific courier.
     */
    @CircuitBreaker(name = "deliveryService", fallbackMethod = "getCourierFallback")
    public DeliveryResponse getCourier(Long id) {
        log.debug("Fetching courier #{} from delivery-service", id);
        return restTemplate.getForObject(
                deliveryServiceUrl + "/api/deliveries/couriers/{id}",
                DeliveryResponse.class,
                id);
    }

    /**
     * Assign a specific courier to a delivery.
     */
    @CircuitBreaker(name = "deliveryService", fallbackMethod = "assignSpecificCourierFallback")
    public DeliveryResponse assignSpecificCourier(Long deliveryId, Long courierId) {
        log.info(">>> Calling delivery-service to assign courier #{} to delivery #{}", courierId, deliveryId);
        return restTemplate.exchange(
                deliveryServiceUrl + "/api/deliveries/{id}/assign/{courierId}",
                HttpMethod.PUT,
                null,
                DeliveryResponse.class,
                deliveryId,
                courierId
        ).getBody();
    }

    /**
     * Auto-assign an available courier to a delivery.
     */
    @CircuitBreaker(name = "deliveryService", fallbackMethod = "assignCourierFallback")
    public DeliveryResponse assignCourier(Long deliveryId) {
        log.info(">>> Calling delivery-service to auto-assign courier to delivery #{}", deliveryId);
        return restTemplate.exchange(
                deliveryServiceUrl + "/api/deliveries/{id}/assign",
                HttpMethod.PUT,
                null,
                DeliveryResponse.class,
                deliveryId
        ).getBody();
    }

    /**
     * Mark delivery as picked up.
     */
    @CircuitBreaker(name = "deliveryService", fallbackMethod = "markPickedUpFallback")
    public DeliveryResponse markPickedUp(Long deliveryId) {
        log.info(">>> Calling delivery-service to mark delivery #{} as picked up", deliveryId);
        return restTemplate.exchange(
                deliveryServiceUrl + "/api/deliveries/{id}/pickup",
                HttpMethod.PUT,
                null,
                DeliveryResponse.class,
                deliveryId
        ).getBody();
    }

    /**
     * Mark delivery as delivered.
     */
    @CircuitBreaker(name = "deliveryService", fallbackMethod = "markDeliveredFallback")
    public DeliveryResponse markDelivered(Long deliveryId) {
        log.info(">>> Calling delivery-service to mark delivery #{} as delivered", deliveryId);
        return restTemplate.exchange(
                deliveryServiceUrl + "/api/deliveries/{id}/deliver",
                HttpMethod.PUT,
                null,
                DeliveryResponse.class,
                deliveryId
        ).getBody();
    }

    /**
     * Check if a delivery is ready for pickup (by checking kitchen ticket via delivery-service).
     */
    public boolean isReadyForPickup(Long orderId) {
        try {
            DeliveryResponse delivery = restTemplate.getForObject(
                    deliveryServiceUrl + "/api/deliveries/order/{orderId}",
                    DeliveryResponse.class,
                    orderId);
            // If the delivery-service says it's PICKED_UP or later, it was ready
            // For the "waiting for kitchen" check, we'll rely on delivery-service's
            // own kitchen check, which happens when pickup is attempted
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
