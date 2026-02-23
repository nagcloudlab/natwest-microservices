package com.demo.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/reserve")
    public ResponseEntity<Reservation> reserve(@RequestBody Map<String, Object> request) {
        Long orderId = Long.valueOf(request.get("orderId").toString());
        String productId = (String) request.get("productId");
        int quantity = (int) request.get("quantity");

        Reservation reservation = inventoryService.reserve(orderId, productId, quantity);
        return ResponseEntity.ok(reservation);
    }

    @PostMapping("/release/{orderId}")
    public ResponseEntity<Reservation> release(@PathVariable Long orderId) {
        Reservation reservation = inventoryService.release(orderId);
        return ResponseEntity.ok(reservation);
    }
}
