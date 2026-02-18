package com.ftgo.accounting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class AccountingController {

    @Autowired
    private AccountingService accountingService;

    @GetMapping("/order/{orderId}")
    public Payment getPaymentByOrderId(@PathVariable Long orderId) {
        return accountingService.getPaymentByOrderId(orderId);
    }
}
