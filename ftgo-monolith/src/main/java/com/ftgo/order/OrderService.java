package com.ftgo.order;

import com.ftgo.restaurant.MenuItem;
import com.ftgo.restaurant.Restaurant;
import com.ftgo.restaurant.RestaurantService;
import com.ftgo.accounting.AccountingService;
import com.ftgo.kitchen.KitchenService;
import com.ftgo.delivery.DeliveryService;
import com.ftgo.notification.NotificationService;
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
    private RestaurantService restaurantService;

    @Autowired
    private AccountingService accountingService;

    @Autowired
    private KitchenService kitchenService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private NotificationService notificationService;

    // ========================================================================
    // THIS IS THE MONOLITH ADVANTAGE:
    // Single @Transactional wrapping Order + Payment + Kitchen + Delivery +
    // Notification. If ANY step fails, EVERYTHING rolls back automatically.
    // No partial state. No inconsistency. Pure ACID.
    //
    // In microservices, this single transaction becomes IMPOSSIBLE.
    // We'll need the Saga pattern instead (coming in Phase 6).
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

        // Step 5: Authorize payment — calls Mock Stripe
        // If this fails → entire transaction rolls back (no order saved)
        accountingService.authorizePayment(order.getId(), totalAmount, request.paymentMethod());

        // Step 6: Create kitchen ticket
        String itemsSummary = orderItems.stream()
                .map(i -> i.getQuantity() + "x " + i.getMenuItemName())
                .collect(Collectors.joining(", "));
        kitchenService.createTicket(order.getId(), restaurant.getId(), itemsSummary);

        // Step 7: Create delivery
        deliveryService.createDelivery(order.getId(), restaurant.getAddress(), request.deliveryAddress());

        // Step 8: Send notification — calls Mock Twilio
        notificationService.sendOrderConfirmation(order.getId(), request.consumerName(), request.consumerContact());

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
