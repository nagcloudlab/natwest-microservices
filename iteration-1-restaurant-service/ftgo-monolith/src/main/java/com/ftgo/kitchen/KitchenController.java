package com.ftgo.kitchen;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kitchen/tickets")
public class KitchenController {

    @Autowired
    private KitchenService kitchenService;

    @GetMapping
    public List<KitchenTicket> getTickets(@RequestParam(required = false) String status) {
        if (status != null) {
            TicketStatus ticketStatus = TicketStatus.valueOf(status.toUpperCase());
            return kitchenService.getTicketsByStatus(ticketStatus);
        }
        return kitchenService.getAllTickets();
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
