package com.ftgo.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
public class MockStripePaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(BigDecimal amount, String paymentMethod) {
        log.info(">>> Mock Stripe: Charging Rs.{} via {}", amount, paymentMethod);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new PaymentResult(true, "stripe_txn_" + UUID.randomUUID().toString().substring(0, 8));
    }
}
