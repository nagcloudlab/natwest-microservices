package com.ftgo.web;

import com.ftgo.delivery.DeliveryResponse;
import com.ftgo.delivery.DeliveryServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/courier")
public class CourierWebController {

    @Autowired
    private DeliveryServiceClient deliveryServiceClient;

    @GetMapping
    public String courierDashboard(Model model) {
        model.addAttribute("couriers", deliveryServiceClient.getAllCouriers());
        return "courier/dashboard";
    }

    @GetMapping("/{id}/deliveries")
    public String courierDeliveries(@PathVariable Long id, Model model) {
        DeliveryResponse courier = deliveryServiceClient.getCourier(id);
        List<DeliveryResponse> myDeliveries = deliveryServiceClient.getDeliveriesByCourierId(id);
        List<DeliveryResponse> pendingDeliveries = deliveryServiceClient.getAllDeliveries().stream()
                .filter(d -> "PENDING".equals(d.getStatus()))
                .toList();

        Set<Long> readyOrderIds = myDeliveries.stream()
                .filter(d -> "COURIER_ASSIGNED".equals(d.getStatus()))
                .filter(d -> deliveryServiceClient.isReadyForPickup(d.getOrderId()))
                .map(DeliveryResponse::getOrderId)
                .collect(Collectors.toSet());

        model.addAttribute("courier", courier);
        model.addAttribute("myDeliveries", myDeliveries);
        model.addAttribute("pendingDeliveries", pendingDeliveries);
        model.addAttribute("readyOrderIds", readyOrderIds);
        return "courier/deliveries";
    }

    @PostMapping("/deliveries/{deliveryId}/assign")
    public String assignCourier(
            @PathVariable Long deliveryId,
            @RequestParam Long courierId,
            RedirectAttributes redirectAttributes) {
        try {
            deliveryServiceClient.assignSpecificCourier(deliveryId, courierId);
            redirectAttributes.addFlashAttribute("success", "Delivery #" + deliveryId + " assigned to you.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/courier/" + courierId + "/deliveries";
    }

    @PostMapping("/deliveries/{deliveryId}/pickup")
    public String markPickedUp(
            @PathVariable Long deliveryId,
            @RequestParam Long courierId,
            RedirectAttributes redirectAttributes) {
        try {
            deliveryServiceClient.markPickedUp(deliveryId);
            redirectAttributes.addFlashAttribute("success", "Delivery #" + deliveryId + " marked as picked up.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/courier/" + courierId + "/deliveries";
    }

    @PostMapping("/deliveries/{deliveryId}/deliver")
    public String markDelivered(
            @PathVariable Long deliveryId,
            @RequestParam Long courierId,
            RedirectAttributes redirectAttributes) {
        try {
            deliveryServiceClient.markDelivered(deliveryId);
            redirectAttributes.addFlashAttribute("success", "Delivery #" + deliveryId + " marked as delivered!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/courier/" + courierId + "/deliveries";
    }
}
