package com.ftgo.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * REST client for calling kitchen-service from delivery-service.
 *
 * This is SERVICE-TO-SERVICE communication:
 *   Delivery Service → Kitchen Service (not through the monolith!)
 *
 * Iteration 9 change: RestTemplate is now injected as a @LoadBalanced bean.
 * URLs use Eureka service names (http://kitchen-service/...) — no hardcoded host:port.
 */
@Slf4j
@Component
public class KitchenServiceClient {

    private final RestTemplate restTemplate;

    public KitchenServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        log.info("KitchenServiceClient initialized with @LoadBalanced RestTemplate (Eureka discovery)");
    }

    /**
     * Check if the kitchen ticket for the given order is ready for pickup.
     */
    @SuppressWarnings("unchecked")
    public boolean isReadyForPickup(Long orderId) {
        try {
            log.info(">>> Checking kitchen ticket readiness for order #{}", orderId);
            Map<String, Object> ticket = restTemplate.getForObject(
                    "http://kitchen-service/api/kitchen/tickets/order/{orderId}",
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
                    "http://kitchen-service/api/kitchen/tickets/order/{orderId}",
                    Map.class,
                    orderId);
            return ticket != null ? (String) ticket.get("status") : null;
        } catch (Exception e) {
            log.warn(">>> Could not get kitchen ticket status for order #{}: {}", orderId, e.getMessage());
            return null;
        }
    }
}
