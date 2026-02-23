package com.demo.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
    public ResponseEntity<Payment> processPayment(@RequestBody Map<String, Object> request) {
        Long orderId = Long.valueOf(request.get("orderId").toString());
        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        Payment payment = paymentService.processPayment(orderId, amount);
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/refund/{orderId}")
    public ResponseEntity<Payment> refundPayment(@PathVariable Long orderId) {
        Payment payment = paymentService.refundPayment(orderId);
        return ResponseEntity.ok(payment);
    }
}
