package com.ftgo.kitchen;

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
 * Anti-Corruption Layer + Circuit Breaker + Timeout for the Kitchen Service.
 *
 * Replaces the old KitchenService (which had @Autowired KitchenTicketRepository).
 * Same method signatures, so callers (OrderService, RestaurantWebController, DeliveryService)
 * need minimal changes.
 *
 * Fault tolerance patterns applied:
 *
 * | # | Pattern          | Where Applied        | Why It Fits                                    |
 * |---|------------------|----------------------|------------------------------------------------|
 * | 1 | @Timeout         | Constructor          | Prevent hanging on slow kitchen-service        |
 * | 4 | @CircuitBreaker  | createTicket()       | Fail fast when kitchen-service is down         |
 * | 3 | @Fallback        | getTicketsByRestaurantId() | Graceful degradation for UI browsing    |
 *
 * Why NO @Retry on createTicket()?
 *   POST /api/kitchen/tickets is NOT idempotent — retrying could create DUPLICATE tickets!
 *   Compare to getTicketsByRestaurantId() (GET) which is safe to retry.
 */
@Slf4j
@Service("kitchenService")
public class KitchenServiceClient {

    private final RestTemplate restTemplate;
    private final String kitchenServiceUrl;

    /**
     * Pattern 1: @Timeout — RestTemplate connectTimeout + readTimeout.
     */
    public KitchenServiceClient(
            @Value("${kitchen-service.url:http://localhost:8084}") String kitchenServiceUrl,
            @Value("${kitchen-service.connect-timeout:2000}") int connectTimeout,
            @Value("${kitchen-service.read-timeout:3000}") int readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restTemplate = new RestTemplate(factory);
        this.kitchenServiceUrl = kitchenServiceUrl;

        log.info("KitchenServiceClient initialized: url={}, connectTimeout={}ms, readTimeout={}ms",
                kitchenServiceUrl, connectTimeout, readTimeout);
    }

    /**
     * Create a kitchen ticket. Called by OrderService during order creation.
     *
     * Pattern 4: @CircuitBreaker — fail fast if kitchen-service is down.
     * No @Retry — POST is not idempotent (could create duplicate tickets).
     */
    @CircuitBreaker(name = "kitchenService", fallbackMethod = "createTicketFallback")
    public KitchenTicketResponse createTicket(Long orderId, Long restaurantId, String items) {
        log.info(">>> Calling kitchen-service to create ticket for order #{}", orderId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "orderId", orderId,
                "restaurantId", restaurantId,
                "items", items
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(
                kitchenServiceUrl + "/api/kitchen/tickets",
                request,
                KitchenTicketResponse.class);
    }

    /**
     * Accept a ticket. Called by RestaurantWebController.
     */
    @CircuitBreaker(name = "kitchenService", fallbackMethod = "acceptTicketFallback")
    public KitchenTicketResponse acceptTicket(Long ticketId) {
        log.info(">>> Calling kitchen-service to accept ticket #{}", ticketId);
        return restTemplate.exchange(
                kitchenServiceUrl + "/api/kitchen/tickets/{id}/accept",
                HttpMethod.PUT,
                null,
                KitchenTicketResponse.class,
                ticketId
        ).getBody();
    }

    /**
     * Start preparation. Called by RestaurantWebController.
     */
    @CircuitBreaker(name = "kitchenService", fallbackMethod = "startPreparationFallback")
    public KitchenTicketResponse startPreparation(Long ticketId) {
        log.info(">>> Calling kitchen-service to start preparation for ticket #{}", ticketId);
        return restTemplate.exchange(
                kitchenServiceUrl + "/api/kitchen/tickets/{id}/preparing",
                HttpMethod.PUT,
                null,
                KitchenTicketResponse.class,
                ticketId
        ).getBody();
    }

    /**
     * Mark ticket as ready for pickup. Called by RestaurantWebController.
     */
    @CircuitBreaker(name = "kitchenService", fallbackMethod = "markReadyFallback")
    public KitchenTicketResponse markReady(Long ticketId) {
        log.info(">>> Calling kitchen-service to mark ticket #{} as ready", ticketId);
        return restTemplate.exchange(
                kitchenServiceUrl + "/api/kitchen/tickets/{id}/ready",
                HttpMethod.PUT,
                null,
                KitchenTicketResponse.class,
                ticketId
        ).getBody();
    }

    /**
     * Get tickets by restaurant ID. Called by RestaurantWebController for the tickets page.
     *
     * Pattern 3: @Fallback — returns empty list on failure (graceful degradation).
     * Viewing tickets is non-critical — better to show "no tickets" than crash the page.
     */
    @CircuitBreaker(name = "kitchenService", fallbackMethod = "getTicketsByRestaurantIdFallback")
    public List<KitchenTicketResponse> getTicketsByRestaurantId(Long restaurantId) {
        log.debug("Fetching tickets for restaurant #{} from kitchen-service", restaurantId);
        return restTemplate.exchange(
                kitchenServiceUrl + "/api/kitchen/tickets?restaurantId={restaurantId}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<KitchenTicketResponse>>() {},
                restaurantId
        ).getBody();
    }

    /**
     * Get ticket by order ID. Called by DeliveryService to check pickup readiness.
     */
    @CircuitBreaker(name = "kitchenService", fallbackMethod = "getTicketByOrderIdFallback")
    public KitchenTicketResponse getTicketByOrderId(Long orderId) {
        log.debug("Fetching ticket for order #{} from kitchen-service", orderId);
        return restTemplate.getForObject(
                kitchenServiceUrl + "/api/kitchen/tickets/order/{orderId}",
                KitchenTicketResponse.class,
                orderId);
    }

    // ======================== FALLBACK METHODS ========================

    public KitchenTicketResponse createTicketFallback(Long orderId, Long restaurantId, String items, Throwable t) {
        log.error(">>> CIRCUIT BREAKER: Kitchen service unavailable for order #{}. Reason: {}", orderId, t.getMessage());
        throw new RuntimeException("Kitchen service is currently unavailable. Please try again later. (Circuit Breaker active)");
    }

    public KitchenTicketResponse acceptTicketFallback(Long ticketId, Throwable t) {
        log.error(">>> CIRCUIT BREAKER: Cannot accept ticket #{}. Reason: {}", ticketId, t.getMessage());
        throw new RuntimeException("Kitchen service is currently unavailable. Cannot accept ticket.");
    }

    public KitchenTicketResponse startPreparationFallback(Long ticketId, Throwable t) {
        log.error(">>> CIRCUIT BREAKER: Cannot start preparation for ticket #{}. Reason: {}", ticketId, t.getMessage());
        throw new RuntimeException("Kitchen service is currently unavailable. Cannot start preparation.");
    }

    public KitchenTicketResponse markReadyFallback(Long ticketId, Throwable t) {
        log.error(">>> CIRCUIT BREAKER: Cannot mark ticket #{} as ready. Reason: {}", ticketId, t.getMessage());
        throw new RuntimeException("Kitchen service is currently unavailable. Cannot mark ticket as ready.");
    }

    public List<KitchenTicketResponse> getTicketsByRestaurantIdFallback(Long restaurantId, Throwable t) {
        log.warn(">>> FALLBACK: Kitchen service unavailable, returning empty ticket list. Reason: {}", t.getMessage());
        return List.of();
    }

    public KitchenTicketResponse getTicketByOrderIdFallback(Long orderId, Throwable t) {
        log.warn(">>> FALLBACK: Kitchen service unavailable for order #{}. Reason: {}", orderId, t.getMessage());
        return null;
    }
}
