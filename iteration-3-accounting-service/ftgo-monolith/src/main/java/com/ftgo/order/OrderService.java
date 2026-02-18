package com.ftgo.order;

import com.ftgo.restaurant.MenuItem;
import com.ftgo.restaurant.Restaurant;
import com.ftgo.restaurant.RestaurantServiceClient;
import com.ftgo.accounting.AccountingServiceClient;
import com.ftgo.kitchen.KitchenService;
import com.ftgo.delivery.DeliveryService;
import com.ftgo.event.OrderCreatedEvent;
import com.ftgo.event.OrderEventPublisher;
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
    private AccountingServiceClient accountingService;

    @Autowired
    private KitchenService kitchenService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private OrderEventPublisher orderEventPublisher;

    // ========================================================================
    // ITERATION 3: Accounting is now an external service with Circuit Breaker!
    //
    // Extracted so far:
    //   - Restaurant → REST (iteration 1)
    //   - Notification → Kafka async (iteration 2)
    //   - Accounting → REST + Circuit Breaker (iteration 3) ← NEW
    //
    // Payment is CRITICAL — unlike notification, we can't fire-and-forget.
    // If accounting-service is down, the Circuit Breaker trips and rejects
    // the order with a clear error, instead of hanging or cascading failures.
    //
    // Remaining modules (Kitchen, Delivery) are still local.
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

        // Step 5: Authorize payment — calls Accounting Service via REST + Circuit Breaker
        // If accounting-service is down → circuit breaker trips → fallback rejects order
        accountingService.authorizePayment(order.getId(), totalAmount, request.paymentMethod());

        // Step 6: Create kitchen ticket
        String itemsSummary = orderItems.stream()
                .map(i -> i.getQuantity() + "x " + i.getMenuItemName())
                .collect(Collectors.joining(", "));
        kitchenService.createTicket(order.getId(), restaurant.getId(), itemsSummary);

        // Step 7: Create delivery
        deliveryService.createDelivery(order.getId(), restaurant.getAddress(), request.deliveryAddress());

        // Step 8: Publish event to Kafka (ASYNC — replaces synchronous notification call)
        // notification-service will consume this event and send the SMS in the background
        orderEventPublisher.publishOrderCreated(new OrderCreatedEvent(
                order.getId(),
                request.consumerName(),
                request.consumerContact(),
                restaurant.getName(),
                totalAmount
        ));

        // Step 9: Approve the order (JPA dirty-checking auto-saves within transaction)
        order.setStatus(OrderStatus.APPROVED);

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
