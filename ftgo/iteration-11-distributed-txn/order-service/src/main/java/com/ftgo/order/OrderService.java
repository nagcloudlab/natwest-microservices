package com.ftgo.order;

import com.ftgo.restaurant.MenuItem;
import com.ftgo.restaurant.Restaurant;
import com.ftgo.restaurant.RestaurantServiceClient;
import com.ftgo.event.OrderCreatedEvent;
import com.ftgo.event.OrderEventPublisher;
import com.ftgo.saga.OrderSagaOrchestrator;
import com.ftgo.saga.SagaState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RestaurantServiceClient restaurantService;

    @Autowired
    private OrderSagaOrchestrator sagaOrchestrator;

    @Autowired
    private OrderEventPublisher orderEventPublisher;

    // ========================================================================
    // ITERATION 13: Distributed Transaction Management (Saga Pattern)
    //
    // BEFORE (iterations 5-12): Sequential REST calls with NO compensation
    //   authorize payment → create ticket → create delivery → approve order
    //   If delivery-service fails, payment is charged and ticket exists
    //   but neither is rolled back. DATA INCONSISTENCY!
    //
    // AFTER (this iteration): Orchestration-based saga with compensation
    //   Each step has a compensating action (refund, cancel ticket, cancel delivery)
    //   If any step fails, all completed steps are compensated in reverse order
    //   Every step is logged to saga_log table for full observability
    //
    // New: saga/ package with OrderSagaOrchestrator, SagaStep, SagaState,
    //       SagaLog entity, SagaEventPublisher
    // New: Compensation endpoints in accounting, kitchen, delivery services
    // New: GET /api/orders/{id}/saga-log endpoint
    // ========================================================================
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        // Step 1: Validate restaurant exists and is open
        Restaurant restaurant = restaurantService.getRestaurant(request.restaurantId());
        if (!restaurant.isOpen()) {
            throw new RuntimeException("Restaurant is currently closed: " + restaurant.getName());
        }

        // Step 2: Validate menu items and get prices
        List<Long> menuItemIds = request.items().stream()
                .map(OrderItemRequest::menuItemId)
                .toList();
        List<MenuItem> menuItems = restaurantService.getMenuItemsByIds(menuItemIds);

        // Step 3: Build order items and calculate total
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.items()) {
            MenuItem menuItem = menuItems.stream()
                    .filter(mi -> mi.getId().equals(itemReq.menuItemId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Menu item not found: " + itemReq.menuItemId()));

            OrderItem orderItem = new OrderItem(
                    menuItem.getName(),
                    menuItem.getPrice(),
                    itemReq.quantity()
            );
            orderItems.add(orderItem);
            totalAmount = totalAmount.add(menuItem.getPrice().multiply(BigDecimal.valueOf(itemReq.quantity())));
        }

        // Step 4: Create and save the order
        Order order = new Order();
        order.setConsumerId(request.consumerId());
        order.setConsumerName(request.consumerName());
        order.setConsumerContact(request.consumerContact());
        order.setRestaurantId(restaurant.getId());
        order.setRestaurantName(restaurant.getName());
        order.setDeliveryAddress(request.deliveryAddress());
        order.setPaymentMethod(request.paymentMethod());
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(totalAmount);
        order.setCreatedAt(LocalDateTime.now());
        order.setItems(orderItems);
        order = orderRepository.save(order);

        // Step 5: Execute the Saga — authorize payment, create ticket, create delivery
        // Each step has a compensating action that runs if a later step fails.
        // This replaces the sequential REST calls with no compensation from iteration 12.
        String itemsSummary = orderItems.stream()
                .map(i -> i.getQuantity() + "x " + i.getMenuItemName())
                .collect(Collectors.joining(", "));

        SagaState sagaResult = sagaOrchestrator.execute(
                order.getId(),
                totalAmount,
                request.paymentMethod(),
                restaurant.getId(),
                itemsSummary,
                restaurant.getAddress(),
                request.deliveryAddress()
        );

        // Step 6: Update order status based on saga result
        if (sagaResult == SagaState.COMPLETED) {
            order.setStatus(OrderStatus.APPROVED);

            // Publish event to Kafka (ASYNC — notification-service sends SMS)
            orderEventPublisher.publishOrderCreated(new OrderCreatedEvent(
                    order.getId(),
                    request.consumerName(),
                    request.consumerContact(),
                    restaurant.getName(),
                    totalAmount
            ));
        } else {
            order.setStatus(OrderStatus.REJECTED);
        }

        return toResponse(order);
    }

    public OrderResponse getOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        return toResponse(order);
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<OrderResponse> getOrdersByConsumerId(Long consumerId) {
        return orderRepository.findByConsumerId(consumerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        order.setStatus(OrderStatus.CANCELLED);
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getConsumerName(),
                order.getRestaurantId(),
                order.getRestaurantName(),
                order.getDeliveryAddress(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getItems(),
                order.getCreatedAt()
        );
    }
}
