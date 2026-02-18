package com.ftgo.delivery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    @Autowired
    private DeliveryService deliveryService;

    @PostMapping
    public Delivery createDelivery(@RequestBody CreateDeliveryRequest request) {
        return deliveryService.createDelivery(request);
    }

    @GetMapping
    public List<Delivery> getAllDeliveries() {
        return deliveryService.getAllDeliveries();
    }

    @GetMapping("/order/{orderId}")
    public Delivery getDeliveryByOrderId(@PathVariable Long orderId) {
        return deliveryService.getDeliveryByOrderId(orderId);
    }

    @PutMapping("/{id}/assign")
    public Delivery assignCourier(@PathVariable Long id) {
        return deliveryService.assignCourier(id);
    }

    @PutMapping("/{id}/assign/{courierId}")
    public Delivery assignSpecificCourier(@PathVariable Long id, @PathVariable Long courierId) {
        return deliveryService.assignSpecificCourier(id, courierId);
    }

    @PutMapping("/{id}/pickup")
    public Delivery markPickedUp(@PathVariable Long id) {
        return deliveryService.markPickedUp(id);
    }

    @PutMapping("/{id}/deliver")
    public Delivery markDelivered(@PathVariable Long id) {
        return deliveryService.markDelivered(id);
    }

    @GetMapping("/courier/{courierId}")
    public List<Delivery> getDeliveriesByCourierId(@PathVariable Long courierId) {
        return deliveryService.getDeliveriesByCourierId(courierId);
    }

    @GetMapping("/couriers")
    public List<Courier> getAllCouriers() {
        return deliveryService.getAllCouriers();
    }

    @GetMapping("/couriers/{id}")
    public Courier getCourier(@PathVariable Long id) {
        return deliveryService.getCourier(id);
    }
}
