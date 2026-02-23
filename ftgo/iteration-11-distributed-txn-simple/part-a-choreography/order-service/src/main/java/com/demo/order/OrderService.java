package com.demo.order;

import com.demo.order.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, EventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    public Order createOrder(String productId, int quantity, BigDecimal amount) {
        Order order = new Order(productId, quantity, amount);
        order = orderRepository.save(order);
        log.info("Order {} created in PENDING state", order.getId());

        // Publish ORDER_CREATED event — inventory-service will pick this up
        eventPublisher.publish("order-events", order.getId().toString(),
                "ORDER_CREATED", order.getId(), productId, quantity, amount);

        return order;
    }

    public void approveOrder(Long orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(OrderStatus.APPROVED);
            orderRepository.save(order);
            log.info("Order {} APPROVED", orderId);
        });
    }

    public void rejectOrder(Long orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(OrderStatus.REJECTED);
            orderRepository.save(order);
            log.info("Order {} REJECTED", orderId);
        });
    }

    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}
