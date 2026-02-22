package com.ftgo.accounting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class AccountingController {

    @Autowired
    private AccountingService accountingService;

    /**
     * Authorize a payment. Called by the monolith's AccountingServiceClient.
     * POST /api/payments/authorize
     */
    @PostMapping("/authorize")
    public Payment authorizePayment(@RequestBody PaymentRequest request) {
        return accountingService.authorizePayment(
                request.getOrderId(),
                request.getAmount(),
                request.getPaymentMethod());
    }

    @GetMapping("/order/{orderId}")
    public Payment getPaymentByOrderId(@PathVariable Long orderId) {
        return accountingService.getPaymentByOrderId(orderId);
    }
}
