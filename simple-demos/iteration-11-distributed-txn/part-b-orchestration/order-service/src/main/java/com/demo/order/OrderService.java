package com.demo.order;

import com.demo.order.saga.OrderSagaOrchestrator;
import com.demo.order.saga.SagaState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator sagaOrchestrator;

    public OrderService(OrderRepository orderRepository, OrderSagaOrchestrator sagaOrchestrator) {
        this.orderRepository = orderRepository;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    public Order createOrder(String productId, int quantity, java.math.BigDecimal amount) {
        // Step 1: Create the order in PENDING state
        Order order = new Order(productId, quantity, amount);
        order = orderRepository.save(order);
        log.info("Order {} created in PENDING state", order.getId());

        // Step 2: Execute the saga (reserve inventory → process payment)
        SagaState result = sagaOrchestrator.execute(order.getId(), productId, quantity, amount);

        // Step 3: Update order status based on saga result
        if (result == SagaState.COMPLETED) {
            order.setStatus(OrderStatus.APPROVED);
            log.info("Order {} APPROVED", order.getId());
        } else {
            order.setStatus(OrderStatus.REJECTED);
            log.info("Order {} REJECTED — saga compensation completed", order.getId());
        }

        return orderRepository.save(order);
    }

    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}
