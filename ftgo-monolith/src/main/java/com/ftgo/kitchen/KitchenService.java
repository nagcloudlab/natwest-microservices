package com.ftgo.kitchen;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class KitchenService {

    @Autowired
    private KitchenTicketRepository kitchenTicketRepository;

    public KitchenTicket createTicket(Long orderId, Long restaurantId, String items) {
        KitchenTicket ticket = new KitchenTicket();
        ticket.setOrderId(orderId);
        ticket.setRestaurantId(restaurantId);
        ticket.setItems(items);
        ticket.setStatus(TicketStatus.CREATED);
        ticket.setCreatedAt(LocalDateTime.now());
        return kitchenTicketRepository.save(ticket);
    }

    public KitchenTicket acceptTicket(Long ticketId) {
        KitchenTicket ticket = kitchenTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));
        ticket.setStatus(TicketStatus.ACCEPTED);
        ticket.setAcceptedAt(LocalDateTime.now());
        return kitchenTicketRepository.save(ticket);
    }

    public KitchenTicket startPreparation(Long ticketId) {
        KitchenTicket ticket = kitchenTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));
        ticket.setStatus(TicketStatus.PREPARING);
        return kitchenTicketRepository.save(ticket);
    }

    public KitchenTicket markReady(Long ticketId) {
        KitchenTicket ticket = kitchenTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));
        ticket.setStatus(TicketStatus.READY_FOR_PICKUP);
        ticket.setReadyAt(LocalDateTime.now());
        return kitchenTicketRepository.save(ticket);
    }

    public List<KitchenTicket> getTicketsByStatus(TicketStatus status) {
        return kitchenTicketRepository.findByStatus(status);
    }

    public List<KitchenTicket> getAllTickets() {
        return kitchenTicketRepository.findAll();
    }

    public List<KitchenTicket> getTicketsByRestaurantId(Long restaurantId) {
        return kitchenTicketRepository.findByRestaurantId(restaurantId);
    }
}
