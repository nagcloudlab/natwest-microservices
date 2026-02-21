package com.demo.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final BigDecimal DECLINE_THRESHOLD = new BigDecimal("100.00");

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment processPayment(Long orderId, BigDecimal amount) {
        log.info("Processing payment of ${} for order {}", amount, orderId);

        // Simulate payment decline for amounts > $100
        if (amount.compareTo(DECLINE_THRESHOLD) > 0) {
            log.warn("Payment DECLINED for order {} — amount ${} exceeds ${}", orderId, amount, DECLINE_THRESHOLD);
            Payment payment = new Payment(orderId, amount, PaymentStatus.DECLINED);
            return paymentRepository.save(payment);
        }

        Payment payment = new Payment(orderId, amount, PaymentStatus.PROCESSED);
        paymentRepository.save(payment);
        log.info("Payment PROCESSED for order {}", orderId);
        return payment;
    }
}
