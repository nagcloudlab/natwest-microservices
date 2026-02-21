package com.ftgo.kitchen;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kitchen Service — business logic for managing kitchen tickets.
 *
 * KEY DIFFERENCE from the monolith version:
 *   Before (monolith): KitchenService had @Autowired OrderRepository
 *     → directly updated Order status when ticket status changed
 *     → tight coupling between Kitchen and Order modules
 *
 *   After (microservice): KitchenService publishes Kafka events instead
 *     → no OrderRepository, no Order dependency at all
 *     → the monolith's TicketStatusEventConsumer handles Order updates
 *     → loose coupling via domain events
 *
 * This is a textbook example of replacing direct database coupling with
 * event-driven communication (Domain Events pattern).
 */
@Service
public class KitchenService {

    @Autowired
    private KitchenTicketRepository kitchenTicketRepository;

    @Autowired
    private TicketEventPublisher ticketEventPublisher;

    public KitchenTicket createTicket(Long orderId, Long restaurantId, String items) {
        KitchenTicket ticket = new KitchenTicket();
        ticket.setOrderId(orderId);
        ticket.setRestaurantId(restaurantId);
        ticket.setItems(items);
        ticket.setStatus(TicketStatus.CREATED);
        ticket.setCreatedAt(LocalDateTime.now());
        return kitchenTicketRepository.save(ticket);
    }

    @Transactional
    public KitchenTicket acceptTicket(Long ticketId) {
        KitchenTicket ticket = kitchenTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));
        ticket.setStatus(TicketStatus.ACCEPTED);
        ticket.setAcceptedAt(LocalDateTime.now());
        ticket = kitchenTicketRepository.save(ticket);

        // Publish event — monolith will update order status
        ticketEventPublisher.publishStatusChanged(
                new TicketStatusChangedEvent(ticket.getId(), ticket.getOrderId(), "ACCEPTED"));

        return ticket;
    }

    @Transactional
    public KitchenTicket startPreparation(Long ticketId) {
        KitchenTicket ticket = kitchenTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));
        ticket.setStatus(TicketStatus.PREPARING);
        ticket = kitchenTicketRepository.save(ticket);

        // Publish event — replaces direct OrderRepository.save(order)
        ticketEventPublisher.publishStatusChanged(
                new TicketStatusChangedEvent(ticket.getId(), ticket.getOrderId(), "PREPARING"));

        return ticket;
    }

    @Transactional
    public KitchenTicket markReady(Long ticketId) {
        KitchenTicket ticket = kitchenTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));
        ticket.setStatus(TicketStatus.READY_FOR_PICKUP);
        ticket.setReadyAt(LocalDateTime.now());
        ticket = kitchenTicketRepository.save(ticket);

        // Publish event — replaces direct OrderRepository.save(order)
        ticketEventPublisher.publishStatusChanged(
                new TicketStatusChangedEvent(ticket.getId(), ticket.getOrderId(), "READY_FOR_PICKUP"));

        return ticket;
    }

    public KitchenTicket getTicketByOrderId(Long orderId) {
        return kitchenTicketRepository.findByOrderId(orderId);
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
