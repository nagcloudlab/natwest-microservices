package com.ftgo.delivery;

import com.ftgo.kitchen.KitchenTicket;
import com.ftgo.kitchen.KitchenTicketRepository;
import com.ftgo.kitchen.TicketStatus;
import com.ftgo.order.Order;
import com.ftgo.order.OrderRepository;
import com.ftgo.order.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeliveryService {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private KitchenTicketRepository kitchenTicketRepository;

    @Autowired
    private OrderRepository orderRepository;

    public Delivery createDelivery(Long orderId, String pickupAddress, String deliveryAddress) {
        Delivery delivery = new Delivery();
        delivery.setOrderId(orderId);
        delivery.setPickupAddress(pickupAddress);
        delivery.setDeliveryAddress(deliveryAddress);
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

    @Transactional
    public Delivery markPickedUp(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found: " + deliveryId));

        KitchenTicket ticket = kitchenTicketRepository.findByOrderId(delivery.getOrderId());
        if (ticket != null && ticket.getStatus() != TicketStatus.READY_FOR_PICKUP) {
            throw new RuntimeException("Cannot pick up — kitchen ticket is not ready yet (current status: " + ticket.getStatus() + ")");
        }

        delivery.setStatus(DeliveryStatus.PICKED_UP);

        Order order = orderRepository.findById(delivery.getOrderId()).orElse(null);
        if (order != null) {
            order.setStatus(OrderStatus.PICKED_UP);
            orderRepository.save(order);
        }

        return deliveryRepository.save(delivery);
    }

    public boolean isReadyForPickup(Long orderId) {
        KitchenTicket ticket = kitchenTicketRepository.findByOrderId(orderId);
        return ticket != null && ticket.getStatus() == TicketStatus.READY_FOR_PICKUP;
    }

    public Delivery markDelivered(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found: " + deliveryId));
        delivery.setStatus(DeliveryStatus.DELIVERED);
        delivery.setDeliveredAt(LocalDateTime.now());

        Courier courier = courierRepository.findById(delivery.getCourierId())
                .orElseThrow(() -> new RuntimeException("Courier not found: " + delivery.getCourierId()));
        courier.setAvailable(true);
        courierRepository.save(courier);

        // Update the order status to DELIVERED
        Order order = orderRepository.findById(delivery.getOrderId()).orElse(null);
        if (order != null) {
            order.setStatus(OrderStatus.DELIVERED);
            orderRepository.save(order);
        }

        return deliveryRepository.save(delivery);
    }

    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
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
}
