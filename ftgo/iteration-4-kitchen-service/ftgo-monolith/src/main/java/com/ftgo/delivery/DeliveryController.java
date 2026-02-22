package com.ftgo.delivery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    @Autowired
    private DeliveryService deliveryService;

    @GetMapping
    public List<Delivery> getAllDeliveries() {
        return deliveryService.getAllDeliveries();
    }

    @PutMapping("/{id}/assign")
    public Delivery assignCourier(@PathVariable Long id) {
        return deliveryService.assignCourier(id);
    }

    @PutMapping("/{id}/pickup")
    public Delivery markPickedUp(@PathVariable Long id) {
        return deliveryService.markPickedUp(id);
    }

    @PutMapping("/{id}/deliver")
    public Delivery markDelivered(@PathVariable Long id) {
        return deliveryService.markDelivered(id);
    }
}
