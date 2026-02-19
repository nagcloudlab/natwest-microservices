package com.ftgo.kitchen;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Kitchen Service REST API.
 *
 * Endpoints:
 *   POST /api/kitchen/tickets              — Create a new ticket (called by monolith)
 *   GET  /api/kitchen/tickets              — List all tickets (optional ?restaurantId=X filter)
 *   GET  /api/kitchen/tickets/order/{orderId} — Get ticket by order ID (used by delivery checks)
 *   PUT  /api/kitchen/tickets/{id}/accept     — Accept a ticket
 *   PUT  /api/kitchen/tickets/{id}/preparing  — Start preparation
 *   PUT  /api/kitchen/tickets/{id}/ready      — Mark as ready for pickup
 */
@RestController
@RequestMapping("/api/kitchen/tickets")
public class KitchenController {

    @Autowired
    private KitchenService kitchenService;

    @PostMapping
    public KitchenTicket createTicket(@RequestBody CreateTicketRequest request) {
        return kitchenService.createTicket(
                request.getOrderId(),
                request.getRestaurantId(),
                request.getItems());
    }

    @GetMapping
    public List<KitchenTicket> getTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long restaurantId) {
        if (restaurantId != null) {
            return kitchenService.getTicketsByRestaurantId(restaurantId);
        }
        if (status != null) {
            TicketStatus ticketStatus = TicketStatus.valueOf(status.toUpperCase());
            return kitchenService.getTicketsByStatus(ticketStatus);
        }
        return kitchenService.getAllTickets();
    }

    @GetMapping("/order/{orderId}")
    public KitchenTicket getTicketByOrderId(@PathVariable Long orderId) {
        return kitchenService.getTicketByOrderId(orderId);
    }

    @PutMapping("/{id}/accept")
    public KitchenTicket acceptTicket(@PathVariable Long id) {
        return kitchenService.acceptTicket(id);
    }

    @PutMapping("/{id}/preparing")
    public KitchenTicket startPreparation(@PathVariable Long id) {
        return kitchenService.startPreparation(id);
    }

    @PutMapping("/{id}/ready")
    public KitchenTicket markReady(@PathVariable Long id) {
        return kitchenService.markReady(id);
    }
}
