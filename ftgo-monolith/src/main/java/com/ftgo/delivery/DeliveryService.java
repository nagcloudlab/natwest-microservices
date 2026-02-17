package com.ftgo.delivery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeliveryService {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private CourierRepository courierRepository;

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

    public Delivery markPickedUp(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found: " + deliveryId));
        delivery.setStatus(DeliveryStatus.PICKED_UP);
        return deliveryRepository.save(delivery);
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

        return deliveryRepository.save(delivery);
    }

    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }
}
