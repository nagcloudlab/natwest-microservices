package com.ftgo.delivery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Delivery Service — business logic for the delivery lifecycle.
 *
 * KEY DIFFERENCES from the monolith version:
 *   1. No OrderRepository — delivery-service does NOT know about Orders
 *   2. No direct KitchenServiceClient from monolith — uses its own REST client to kitchen-service
 *   3. Status changes publish Kafka events instead of directly updating Order status
 *
 * The monolith's DeliveryStatusEventConsumer listens for these events and updates
 * Order status accordingly. This is the same pattern used for kitchen-service in iteration 4.
 */
@Service
public class DeliveryService {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private KitchenServiceClient kitchenServiceClient;

    @Autowired
    private DeliveryEventPublisher eventPublisher;

    public Delivery createDelivery(CreateDeliveryRequest request) {
        Delivery delivery = new Delivery();
        delivery.setOrderId(request.getOrderId());
        delivery.setPickupAddress(request.getPickupAddress());
        delivery.setDeliveryAddress(request.getDeliveryAddress());
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setCreatedAt(LocalDateTime.now());
        return deliveryRepository.save(delivery);
    }

    public Delivery assignCourier(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found: " + deliveryId));

        Courier courier = courierRepository.findFirstByAvailableTrue()
                .orElseThrow(() -> new RuntimeException("No courier available"));

        delivery.setCourierId(courier.getId());
        delivery.setStatus(DeliveryStatus.COURIER_ASSIGNED);

        courier.setAvailable(false);
        courierRepository.save(courier);

        return deliveryRepository.save(delivery);
    }

    public Delivery assignSpecificCourier(Long deliveryId, Long courierId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found: " + deliveryId));

        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found: " + courierId));

        delivery.setCourierId(courier.getId());
        delivery.setStatus(DeliveryStatus.COURIER_ASSIGNED);

        courier.setAvailable(false);
        courierRepository.save(courier);

        return deliveryRepository.save(delivery);
    }

    /**
     * Mark delivery as picked up.
     *
     * CROSS-SERVICE CALL: Checks kitchen-service to verify the ticket is READY_FOR_PICKUP.
     * This is service-to-service communication — delivery-service calls kitchen-service directly.
     *
     * Instead of updating Order status directly (as in the monolith), publishes a Kafka event.
     * The monolith's DeliveryStatusEventConsumer will update the Order status.
     */
    @Transactional
    public Delivery markPickedUp(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found: " + deliveryId));

        // Cross-service call: delivery-service → kitchen-service
        if (!kitchenServiceClient.isReadyForPickup(delivery.getOrderId())) {
            String ticketStatus = kitchenServiceClient.getTicketStatus(delivery.getOrderId());
            throw new RuntimeException("Cannot pick up — kitchen ticket is not ready yet (current status: " + ticketStatus + ")");
        }

        delivery.setStatus(DeliveryStatus.PICKED_UP);
        delivery = deliveryRepository.save(delivery);

        // Publish event to Kafka instead of directly updating Order
        eventPublisher.publishStatusChanged(new DeliveryStatusChangedEvent(
                delivery.getId(), delivery.getOrderId(), "PICKED_UP"));

        return delivery;
    }

    /**
     * Mark delivery as delivered.
     *
     * Frees the courier and publishes a Kafka event.
     * The monolith's DeliveryStatusEventConsumer will update the Order status to DELIVERED.
     */
    @Transactional
    public Delivery markDelivered(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found: " + deliveryId));
        delivery.setStatus(DeliveryStatus.DELIVERED);
        delivery.setDeliveredAt(LocalDateTime.now());

        // Free the courier
        Long courierId = delivery.getCourierId();
        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found: " + courierId));
        courier.setAvailable(true);
        courierRepository.save(courier);

        delivery = deliveryRepository.save(delivery);

        // Publish event to Kafka instead of directly updating Order
        eventPublisher.publishStatusChanged(new DeliveryStatusChangedEvent(
                delivery.getId(), delivery.getOrderId(), "DELIVERED"));

        return delivery;
    }

    public boolean isReadyForPickup(Long orderId) {
        return kitchenServiceClient.isReadyForPickup(orderId);
    }

    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    public Delivery getDeliveryByOrderId(Long orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Delivery not found for order: " + orderId));
    }

    public List<Delivery> getDeliveriesByCourierId(Long courierId) {
        return deliveryRepository.findByCourierId(courierId);
    }

    public Courier getCourier(Long id) {
        return courierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Courier not found: " + id));
    }

    public List<Courier> getAllCouriers() {
        return courierRepository.findAll();
    }
}
