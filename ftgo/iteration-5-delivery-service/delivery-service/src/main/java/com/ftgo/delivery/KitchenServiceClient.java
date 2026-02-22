package com.ftgo.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * REST client for calling kitchen-service from delivery-service.
 *
 * This is the first example of SERVICE-TO-SERVICE communication:
 *   Delivery Service → Kitchen Service (not through the monolith!)
 *
 * The delivery-service needs to check if a kitchen ticket is ready for pickup
 * before allowing the courier to pick up the order. Instead of going through
 * the monolith, it calls kitchen-service directly.
 *
 * This is a simple RestTemplate-based client (no circuit breaker here —
 * the delivery-service is a lightweight microservice, not the monolith).
 */
@Slf4j
@Component
public class KitchenServiceClient {

    private final RestTemplate restTemplate;
    private final String kitchenServiceUrl;

    public KitchenServiceClient(
            @Value("${kitchen-service.url:http://localhost:8084}") String kitchenServiceUrl) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);

        this.restTemplate = new RestTemplate(factory);
        this.kitchenServiceUrl = kitchenServiceUrl;

        log.info("KitchenServiceClient initialized: url={}", kitchenServiceUrl);
    }

    /**
     * Check if the kitchen ticket for the given order is ready for pickup.
     */
    @SuppressWarnings("unchecked")
    public boolean isReadyForPickup(Long orderId) {
        try {
            log.info(">>> Checking kitchen ticket readiness for order #{}", orderId);
            Map<String, Object> ticket = restTemplate.getForObject(
                    kitchenServiceUrl + "/api/kitchen/tickets/order/{orderId}",
                    Map.class,
                    orderId);
            if (ticket != null && "READY_FOR_PICKUP".equals(ticket.get("status"))) {
                return true;
            }
            log.info(">>> Kitchen ticket for order #{} is not ready yet (status: {})",
                    orderId, ticket != null ? ticket.get("status") : "NOT_FOUND");
            return false;
        } catch (Exception e) {
            log.warn(">>> Could not check kitchen ticket for order #{}: {}", orderId, e.getMessage());
            return false;
        }
    }

    /**
     * Get the current status of the kitchen ticket for the given order.
     */
    @SuppressWarnings("unchecked")
    public String getTicketStatus(Long orderId) {
        try {
            Map<String, Object> ticket = restTemplate.getForObject(
                    kitchenServiceUrl + "/api/kitchen/tickets/order/{orderId}",
                    Map.class,
                    orderId);
            return ticket != null ? (String) ticket.get("status") : null;
        } catch (Exception e) {
            log.warn(">>> Could not get kitchen ticket status for order #{}: {}", orderId, e.getMessage());
            return null;
        }
    }
}
