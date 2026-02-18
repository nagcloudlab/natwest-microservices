package com.ftgo.accounting;

import com.ftgo.external.PaymentGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class AccountingService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentGateway paymentGateway;

    public Payment authorizePayment(Long orderId, BigDecimal amount, String paymentMethod) {
        PaymentGateway.PaymentResult result = paymentGateway.charge(amount, paymentMethod);

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setPaymentMethod(paymentMethod);
        payment.setCreatedAt(LocalDateTime.now());

        if (result.success()) {
            payment.setStatus(PaymentStatus.AUTHORIZED);
            payment.setTransactionId(result.transactionId());
            return paymentRepository.save(payment);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new RuntimeException("Payment authorization failed for order: " + orderId);
        }
    }

    public Payment getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));
    }
}
